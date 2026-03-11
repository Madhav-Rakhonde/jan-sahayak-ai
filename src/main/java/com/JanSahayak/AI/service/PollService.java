package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.payload.request.*;
import com.JanSahayak.AI.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

    private final PollRepository        pollRepository;
    private final PollOptionRepository  pollOptionRepository;
    private final PollVoteRepository    pollVoteRepository;
    private final SocialPostRepo  socialPostRepository;
    private final UserRepo       userRepository;

    // FIX #3: CommunityService was not injected — polls in communities were invisible
    // in the community feed and post counters were never incremented.
    // @Lazy breaks the circular dependency: CommunityService → SocialPostRepo,
    // PollService → CommunityService.
    @Lazy
    @Autowired
    private CommunityService communityService;


    @Transactional
    public PollResponse createPollPost(CreatePollRequest req, User creator) {

        // ── Step 1: Validate ──────────────────────────────────────────────────
        validatePollRequest(req);

        // ── Step 2: Auto-create SocialPost ────────────────────────────────────
        // Content = poll question (this is what appears in the feed as the post text)
        SocialPost socialPost = SocialPost.builder()
                .content(req.getQuestion())          // poll question becomes post content
                .user(creator)
                .status(PostStatus.ACTIVE)
                .allowComments(true)
                .build();

        // Inherit location from user's pincode (your existing method)
        socialPost.inheritLocationFromUser(creator);

        SocialPost savedSocialPost = socialPostRepository.save(socialPost);
        log.info("Auto-created SocialPost {} for poll by user {}", savedSocialPost.getId(), creator.getId());

        // FIX #3: Wire onPostPublished so polls in communities are visible in the
        // community feed and community post/stats counters are correctly incremented.
        if (savedSocialPost.getCommunityId() != null) {
            try {
                communityService.onPostPublished(savedSocialPost, savedSocialPost.getCommunityId());
            } catch (Exception e) {
                log.warn("[Community] onPostPublished failed for poll post={} community={}: {}",
                        savedSocialPost.getId(), savedSocialPost.getCommunityId(), e.getMessage());
            }
        }

        Poll poll = buildPoll(req, creator, savedSocialPost);
        Poll savedPoll = pollRepository.save(poll);


        attachOptions(savedPoll, req.getOptions());

        log.info("Created Poll {} with {} options for SocialPost {}",
                savedPoll.getId(), req.getOptions().size(), savedSocialPost.getId());


        return PollResponse.from(savedPoll, false, true, List.of());
    }


    @Transactional
    public PollResponse vote(Long pollId, List<Long> optionIds, User voter) {
        Poll poll = pollRepository.findByIdWithOptions(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        if (!poll.isOpenForVoting()) {
            throw new IllegalStateException("This poll is closed.");
        }

        boolean alreadyVoted = pollVoteRepository.existsByPollIdAndUserId(pollId, voter.getId());
        if (alreadyVoted) {
            throw new IllegalStateException("You have already voted in this poll.");
        }

        if (!Boolean.TRUE.equals(poll.getAllowMultipleVotes()) && optionIds.size() > 1) {
            throw new IllegalArgumentException("This poll only allows one choice.");
        }

        for (Long optionId : optionIds) {
            PollOption option = pollOptionRepository.findById(optionId)
                    .orElseThrow(() -> new RuntimeException("Option not found: " + optionId));

            if (!option.getPoll().getId().equals(pollId)) {
                throw new IllegalArgumentException("Option does not belong to this poll.");
            }

            pollVoteRepository.save(PollVote.builder()
                    .poll(poll)
                    .user(voter)
                    .pollOption(option)
                    .build());

            option.incrementVoteCount();
            pollOptionRepository.save(option);
            poll.incrementTotalVotes();
        }

        Poll updatedPoll = pollRepository.save(poll);
        List<Long> votedIds = pollVoteRepository.findOptionIdsByPollIdAndUserId(pollId, voter.getId());
        return PollResponse.from(updatedPoll, true, true, votedIds);
    }


    public PollResponse getPollResponse(Long pollId, User requestingUser) {
        Poll poll = pollRepository.findByIdWithOptions(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        boolean userHasVoted = pollVoteRepository.existsByPollIdAndUserId(pollId, requestingUser.getId());
        boolean showResults  = poll.shouldShowResults(userHasVoted);
        List<Long> votedIds  = userHasVoted
                ? pollVoteRepository.findOptionIdsByPollIdAndUserId(pollId, requestingUser.getId())
                : List.of();

        return PollResponse.from(poll, userHasVoted, showResults, votedIds);
    }

    public PollResponse getPollBySocialPostId(Long socialPostId, User requestingUser) {
        Poll poll = pollRepository.findBySocialPostId(socialPostId)
                .orElseThrow(() -> new RuntimeException("No poll found for this post."));
        return getPollResponse(poll.getId(), requestingUser);
    }

    @Transactional
    public void closePoll(Long pollId, User requestingUser) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        boolean isOwner = poll.getCreatedBy().getId().equals(requestingUser.getId());
        if (!isOwner && !requestingUser.isAdmin()) {
            throw new SecurityException("Not allowed to close this poll.");
        }

        poll.deactivate();
        pollRepository.save(poll);
        log.info("Poll {} closed by user {}", pollId, requestingUser.getId());
    }

    private void validatePollRequest(CreatePollRequest req) {
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Poll question cannot be empty.");
        }
        if (req.getQuestion().length() > 500) {
            throw new IllegalArgumentException("Poll question cannot exceed 500 characters.");
        }
        if (req.getOptions() == null || req.getOptions().size() < 2 || req.getOptions().size() > 4) {
            throw new IllegalArgumentException("A poll must have between 2 and 4 options.");
        }
        for (String opt : req.getOptions()) {
            if (opt == null || opt.isBlank()) {
                throw new IllegalArgumentException("Option text cannot be blank.");
            }
            if (opt.length() > 200) {
                throw new IllegalArgumentException("Option text cannot exceed 200 characters.");
            }
        }
    }

    private Poll buildPoll(CreatePollRequest req, User creator, SocialPost socialPost) {
        // Calculate expiresAt from duration string ("1d", "3d", "7d", "never")
        Date expiresAt = null;
        if (req.getExpiresIn() != null && !req.getExpiresIn().equals("never")) {
            int days = switch (req.getExpiresIn()) {
                case "1d" -> 1;
                case "3d" -> 3;
                case "7d" -> 7;
                default   -> 1;
            };
            expiresAt = new Date(System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000);
        }

        return Poll.builder()
                .question(req.getQuestion())
                .socialPost(socialPost)
                .createdBy(creator)
                .expiresAt(expiresAt)
                .allowMultipleVotes(Boolean.TRUE.equals(req.getAllowMultipleVotes()))
                .showResultsBeforeExpiry(req.getShowResultsBeforeExpiry() == null || req.getShowResultsBeforeExpiry())
                .build();
    }

    private void attachOptions(Poll poll, List<String> optionTexts) {
        IntStream.range(0, optionTexts.size()).forEach(i -> {
            PollOption option = PollOption.builder()
                    .poll(poll)
                    .optionText(optionTexts.get(i).trim())
                    .optionOrder(i + 1)
                    .build();
            pollOptionRepository.save(option);
            poll.getOptions().add(option);
        });
    }
}