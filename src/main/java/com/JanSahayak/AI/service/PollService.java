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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

    private final PollRepository        pollRepository;
    private final PollOptionRepository  pollOptionRepository;
    private final PollVoteRepository    pollVoteRepository;
    private final SocialPostRepo        socialPostRepository;
    private final UserRepo              userRepository;

    // @Lazy breaks the circular dependency: CommunityService → SocialPostRepo,
    // PollService → CommunityService.
    @Lazy
    @Autowired
    private CommunityService communityService;


    @Transactional
    public PollResponse createPollPost(CreatePollRequest req, User creator) {

        validatePollRequest(req);

        SocialPost socialPost = SocialPost.builder()
                .content(req.getQuestion())
                .user(creator)
                .status(PostStatus.ACTIVE)
                .allowComments(true)
                .build();

        socialPost.inheritLocationFromUser(creator);

        SocialPost savedSocialPost = socialPostRepository.save(socialPost);
        log.info("Auto-created SocialPost {} for poll by user {}", savedSocialPost.getId(), creator.getId());

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

        // Check for existing votes (for re-voting/un-voting)
        List<PollVote> existingVotes = pollVoteRepository.findByPollIdAndUserId(pollId, voter.getId());
        if (!existingVotes.isEmpty()) {
            for (PollVote vote : existingVotes) {
                PollOption opt = vote.getPollOption();
                opt.decrementVoteCount();
                poll.decrementTotalVotes();
            }
            pollVoteRepository.deleteAll(existingVotes);
            pollOptionRepository.saveAll(existingVotes.stream()
                .map(PollVote::getPollOption)
                .collect(Collectors.toList()));
        }

        // If optionIds is empty, we consider it an "un-vote" and return early
        if (optionIds == null || optionIds.isEmpty()) {
            Poll updatedPoll = pollRepository.save(poll);
            return PollResponse.from(updatedPoll, false, true, List.of());
        }

        if (!Boolean.TRUE.equals(poll.getAllowMultipleVotes()) && optionIds.size() > 1) {
            throw new IllegalArgumentException("This poll only allows one choice.");
        }

        List<PollOption> newOptions = pollOptionRepository.findAllById(optionIds);
        if (newOptions.size() != optionIds.size()) {
            throw new RuntimeException("One or more poll options not found.");
        }

        List<PollVote> votes = new ArrayList<>();
        for (PollOption option : newOptions) {
            if (!option.getPoll().getId().equals(pollId)) {
                throw new IllegalArgumentException("Option " + option.getId() + " does not belong to this poll.");
            }
            votes.add(PollVote.builder()
                    .poll(poll)
                    .user(voter)
                    .pollOption(option)
                    .build());
            option.incrementVoteCount();
            poll.incrementTotalVotes();
        }

        pollVoteRepository.saveAll(votes);
        pollOptionRepository.saveAll(newOptions);

        Poll updatedPoll = pollRepository.save(poll);
        List<Long> votedIds = pollVoteRepository.findOptionIdsByPollIdAndUserId(pollId, voter.getId());
        return PollResponse.from(updatedPoll, !votedIds.isEmpty(), true, votedIds);
    }


    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    /**
     * FIX: Replaced per-option pollOptionRepository.save() inside a forEach loop with
     * a single pollOptionRepository.saveAll() call. The old code issued one INSERT
     * per option (up to 4 inserts per poll creation). saveAll() issues a single
     * batch INSERT regardless of how many options are being saved.
     */
    private void attachOptions(Poll poll, List<String> optionTexts) {
        List<PollOption> options = IntStream.range(0, optionTexts.size())
                .mapToObj(i -> PollOption.builder()
                        .poll(poll)
                        .optionText(optionTexts.get(i).trim())
                        .optionOrder(i + 1)
                        .build())
                .collect(Collectors.toList());

        pollOptionRepository.saveAll(options);  // single batch INSERT
        poll.getOptions().addAll(options);
    }
}