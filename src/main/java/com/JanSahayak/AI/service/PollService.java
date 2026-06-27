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
import org.springframework.web.multipart.MultipartFile;
import com.JanSahayak.AI.service.SocialPostMediaService;
import com.JanSahayak.AI.payload.SocialPostUtility;
import com.JanSahayak.AI.exception.ServiceException;

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
    private final SocialPostMediaService mediaService;

    // @Lazy breaks the circular dependency: CommunityService → SocialPostRepo,
    // PollService → CommunityService.
    @Lazy
    @Autowired
    private CommunityService communityService;


    @Transactional(rollbackFor = Exception.class)
    public PollResponse createPollPost(CreatePollRequest req, User creator) {

        validatePollRequest(req);

        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        if (idempotencyKey != null) {
            java.util.Optional<Poll> existingPoll = pollRepository.findByIdempotencyKey(idempotencyKey);
            if (existingPoll.isPresent()) {
                log.info("Idempotency hit: Returning existing Poll for key {}", idempotencyKey);
                return PollResponse.from(existingPoll.get(), false, true, List.of());
            }
        }

        SocialPost socialPost = SocialPost.builder()
                .content(req.getQuestion())
                .user(creator)
                .status(PostStatus.ACTIVE)
                .allowComments(true)
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
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

    @Transactional(rollbackFor = Exception.class)
    public PollResponse createPollPostWithMedia(CreatePollRequest req, List<MultipartFile> mediaFiles, User creator) {
        validatePollRequest(req);
        
        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        if (idempotencyKey != null) {
            java.util.Optional<Poll> existingPoll = pollRepository.findByIdempotencyKey(idempotencyKey);
            if (existingPoll.isPresent()) {
                log.info("Idempotency hit: Returning existing Poll with media for key {}", idempotencyKey);
                return PollResponse.from(existingPoll.get(), false, true, List.of());
            }
        }

        // 1. Upload media files with validation
        List<String> uploadedMediaUrls = new ArrayList<>();
        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            uploadedMediaUrls = uploadMediaFilesWithValidation(mediaFiles, creator.getId());
        }
        // 2. Build the parent SocialPost
        SocialPost socialPost = SocialPost.builder()
                .content(req.getQuestion())
                .user(creator)
                .status(PostStatus.ACTIVE)
                .allowComments(true)
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
                .build();
        socialPost.inheritLocationFromUser(creator);
        // 3. Attach media URLs if uploaded
        if (!uploadedMediaUrls.isEmpty()) {
            socialPost.setMediaUrlsList(uploadedMediaUrls);
        }
        SocialPost savedSocialPost = socialPostRepository.save(socialPost);
        log.info("Auto-created SocialPost {} with {} media files for poll by user {}", 
                savedSocialPost.getId(), savedSocialPost.getMediaCount(), creator.getId());
        // 4. Trigger community published event if post belongs to a community
        if (savedSocialPost.getCommunityId() != null) {
            try {
                communityService.onPostPublished(savedSocialPost, savedSocialPost.getCommunityId());
            } catch (Exception e) {
                log.warn("[Community] onPostPublished failed for poll post={} community={}: {}",
                        savedSocialPost.getId(), savedSocialPost.getCommunityId(), e.getMessage());
            }
        }
        // 5. Build and attach the Poll object to the SocialPost
        Poll poll = buildPoll(req, creator, savedSocialPost);
        Poll savedPoll = pollRepository.save(poll);
        attachOptions(savedPoll, req.getOptions());
        log.info("Created Poll {} with {} options for SocialPost {}",
                savedPoll.getId(), req.getOptions().size(), savedSocialPost.getId());
        return PollResponse.from(savedPoll, false, true, List.of());
    }

    private List<String> uploadMediaFilesWithValidation(List<MultipartFile> files, Long userId) {
        try {
            SocialPostUtility.validateMediaFiles(files);
            return mediaService.uploadMediaFiles(files, userId);
        } catch (Exception e) {
            log.error("Failed to upload media files for user: {}", userId, e);
            throw new ServiceException("Failed to upload media files: " + e.getMessage(), e);
        }
    }


    @Transactional(rollbackFor = Exception.class)
    public PollResponse vote(Long pollId, List<Long> optionIds, User voter) {
        Poll poll = pollRepository.findByIdWithOptions(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found: " + pollId));

        if (!poll.isOpenForVoting()) {
            throw new IllegalStateException("This poll is closed.");
        }

        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();
        if (idempotencyKey != null) {
            java.util.Optional<PollVote> existingVote = pollVoteRepository.findByIdempotencyKey(idempotencyKey);
            if (existingVote.isPresent()) {
                log.info("Idempotency hit: Returning existing vote for key {}", idempotencyKey);
                List<Long> votedIds = pollVoteRepository.findOptionIdsByPollIdAndUserId(pollId, voter.getId());
                return PollResponse.from(poll, true, true, votedIds);
            }
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
                    .idempotencyKey(idempotencyKey)
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

    @Transactional(rollbackFor = Exception.class)
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

        String idempotencyKey = com.JanSahayak.AI.util.IdempotencyContext.getKey();

        return Poll.builder()
                .question(req.getQuestion())
                .socialPost(socialPost)
                .createdBy(creator)
                .expiresAt(expiresAt)
                .idempotencyKey(idempotencyKey)
                .allowMultipleVotes(Boolean.TRUE.equals(req.getAllowMultipleVotes()))
                .showResultsBeforeExpiry(req.getShowResultsBeforeExpiry() == null || req.getShowResultsBeforeExpiry())
                .ipAddress(com.JanSahayak.AI.util.IpUtils.getClientIpFromContext())
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