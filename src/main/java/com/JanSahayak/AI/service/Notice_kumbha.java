package com.JanSahayak.AI.service;

import com.JanSahayak.AI.DTO.PaginatedResponse;
import com.JanSahayak.AI.DTO.PostResponse;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.PostSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class Notice_kumbha {

    private static final Logger log = LoggerFactory.getLogger(Notice_kumbha.class);

    private final PostRepo postRepository;
    private final PostService postService;

    public Notice_kumbha(PostRepo postRepository, PostService postService) {
        this.postRepository = postRepository;
        this.postService = postService;
    }

    public PaginatedResponse<PostResponse> searchPostsByWildcardPatterns(User currentUser,
                                                                         List<String> patterns,
                                                                         List<String> roleNames,
                                                                         Long beforePostId,
                                                                         Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            if (patterns == null || patterns.isEmpty()) {
                throw new ValidationException("Search patterns cannot be empty");
            }

            if (roleNames == null || roleNames.isEmpty()) {
                throw new ValidationException("Role names list cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchPostsByWildcardPatterns", beforePostId, limit);

            Specification<Post> spec = PostSpecification.hasWildcardHashtags(patterns, roleNames);

            if (setup.hasCursor()) {
                spec = spec.and((root, query, cb) -> cb.lessThan(root.get("id"), setup.getSanitizedCursor()));
            }

            Page<Post> postPage = postRepository.findAll(spec, setup.toPageable());

            List<PostResponse> postResponses = postPage.getContent().stream()
                    .map(post -> postService.convertToPostResponse(post, currentUser))
                    .collect(Collectors.toList());
            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            if (postPage.hasNext() != response.isHasMore()) {
                response = PaginatedResponse.of(
                        postResponses,
                        postPage.hasNext(),
                        response.getNextCursor(),
                        setup.getValidatedLimit()
                );
            }

            PaginationUtils.logPaginationResults("searchPostsByWildcardPatterns", postResponses,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (ValidationException e) {
            log.error("Validation error in searchPostsByWildcardPatterns: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to search posts by wildcard patterns for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchPostsByWildcardPatterns", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<PostResponse> searchLostItemsPostsByWildcard(User currentUser,
                                                                          Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            List<String> lostItemPatterns = List.of(
                    "#lost%", "#found%", "lost item", "found item", "lost pet", "found pet",
                    "#lostbag", "#foundwallet", "#missingdog", "#lostkey", "#foundphone"
            );

            List<String> targetRoles = List.of(Constant.ROLE_USER, Constant.ROLE_DEPARTMENT);

            return searchPostsByWildcardPatterns(currentUser, lostItemPatterns, targetRoles, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search lost item posts by wildcard for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchLostItemsPostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    public PaginatedResponse<PostResponse> searchMissingPersonPostsByWildcard(User currentUser,
                                                                              Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            List<String> missingPersonPatterns = List.of(
                    "#missing%", "missing person", "#missingchild", "#amberalert"
            );

            List<String> targetRoles = List.of(Constant.ROLE_USER, Constant.ROLE_DEPARTMENT);

            return searchPostsByWildcardPatterns(currentUser, missingPersonPatterns, targetRoles, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search missing person posts by wildcard for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchMissingPersonPostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Searches for posts related to theft issues.
     */
    public PaginatedResponse<PostResponse> searchTheftIssuePostsByWildcard(User currentUser,
                                                                           Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            List<String> theftPatterns = List.of(
                    "#theft", "#stolen", "#robbery", "theft alert", "#crime"
            );

            List<String> targetRoles = List.of(Constant.ROLE_USER);

            return searchPostsByWildcardPatterns(currentUser, theftPatterns, targetRoles, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search theft issue posts for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchTheftIssuePostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Searches for posts related to free food distribution.
     */
    public PaginatedResponse<PostResponse> searchFreeFoodPostsByWildcard(User currentUser,
                                                                         Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            List<String> foodPatterns = List.of(
                    "#freefood", "#fooddonation", "langar", "#bhandara", "#fooddrive", "ngo food"
            );

            List<String> targetRoles = List.of(Constant.ROLE_USER, Constant.ROLE_DEPARTMENT);

            return searchPostsByWildcardPatterns(currentUser, foodPatterns, targetRoles, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search free food posts for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchFreeFoodPostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Searches for posts related to health camps and medical services.
     */
    public PaginatedResponse<PostResponse> searchHealthCampPostsByWildcard(User currentUser,
                                                                           Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            List<String> healthPatterns = List.of(
                    "#healthcamp", "#medicalcamp", "free checkup", "#blooddonation", "#freehealthcheckup"
            );
            // Primarily from departments, but NGOs (users) can also post.
            List<String> targetRoles = List.of(Constant.ROLE_DEPARTMENT);

            return searchPostsByWildcardPatterns(currentUser, healthPatterns, targetRoles, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search health camp posts for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchHealthCampPostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Searches for official notices and stamped documents.
     */
    public PaginatedResponse<PostResponse> searchStampedPostsByWildcard(User currentUser,
                                                                        Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            List<String> stampedPatterns = List.of(
                    "#stamped", "#stampedalter"
            );
            // Now allows posts from both departments and normal users.
            List<String> targetRoles = List.of(Constant.ROLE_DEPARTMENT, Constant.ROLE_USER);

            return searchPostsByWildcardPatterns(currentUser, stampedPatterns, targetRoles, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search stamped posts for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchStampedPostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }
    public PaginatedResponse<PostResponse> searchweather(User currentUser,
                                                                        Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            List<String> stampedPatterns = List.of(
                    "#highpriority%",
                    "%#highpriority%",
                    "#weatheremergency%",
                    "%#weatheremergency%",
                    "#weatheralert%",
                    "%#weatheralert%",
//                    "#trafficupdate%",
//                    "%#trafficupdate%",
                    "%high%priority%",
                    "%weather%emergency%",
                    "%weather%alert%"
//                    "%traffic%update%"
            );
            // Now allows posts from both departments and normal users.
            List<String> targetRoles = List.of(Constant.ROLE_DEPARTMENT);

            return searchPostsByWildcardPatterns(currentUser, stampedPatterns, targetRoles, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search stamped posts for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchStampedPostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }
}

