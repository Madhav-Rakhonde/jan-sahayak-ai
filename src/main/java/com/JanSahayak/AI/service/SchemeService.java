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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchemeService {

    // Inject dependencies through constructor (RequiredArgsConstructor handles this)
    private final PostRepo postRepository;
    private final PostService postService;

    public PaginatedResponse<PostResponse> searchAgriculturePostsByWildcard(User currentUser,
                                                                            Long beforePostId, Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            // Define flexible list of wildcard patterns for agriculture-related posts
            List<String> agriculturePatterns = List.of(
                    "#agri%",
                    "%farmer%",
                    "#crop%",
                    "#agriculture%",
                    "#farming%",
                    "#kisan%",
                    "#seed%",
                    "#harvest%",
                    "#irrigation%",
                    "#fertilizer%",
                    "#pesticide%",
                    "#organic%",
                    "#pmkisan%",
                    "#msp%",
                    "#fasal%",
                    "#krishi%",
                    "#soil%",
                    "#weather%",
                    "#monsoon%",
                    "#dairy%",
                    "#livestock%",
                    "#poultry%",
                    "#fisheries%",
                    "#horticulture%",
                    "#plantation%",
                    "%agriculture%",
                    "%farming%",
                    "%kisan%",
                    "%crop%",
                    "%harvest%"
            );

            return searchPostsByWildcardPatterns(currentUser, agriculturePatterns, Constant.ROLE_DEPARTMENT, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search agriculture posts by wildcard for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchAgriculturePostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Search posts by custom wildcard patterns and role
     */
    public PaginatedResponse<PostResponse> searchPostsByWildcardPatterns(User currentUser,
                                                                         List<String> patterns,
                                                                         String roleName,
                                                                         Long beforePostId,
                                                                         Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            if (patterns == null || patterns.isEmpty()) {
                throw new ValidationException("Search patterns cannot be empty");
            }

            if (roleName == null || roleName.trim().isEmpty()) {
                throw new ValidationException("Role name cannot be empty");
            }

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchPostsByWildcardPatterns", beforePostId, limit);

            // Build the specification
            Specification<Post> spec = PostSpecification.hasWildcardHashtags(patterns, roleName);

            // Add cursor-based pagination if beforePostId is provided
            if (setup.hasCursor()) {
                spec = spec.and((root, query, cb) -> cb.lessThan(root.get("id"), setup.getSanitizedCursor()));
            }

            // Execute the query
            Page<Post> postPage = postRepository.findAll(spec, setup.toPageable());

            // Convert to post responses
            List<PostResponse> postResponses = postPage.getContent().stream()
                    .map(post -> postService.convertToPostResponse(post, currentUser))
                    .collect(Collectors.toList());
            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            // Override hasMore from page information if 6
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
            throw e;
        } catch (Exception e) {
            log.error("Failed to search posts by wildcard patterns for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchPostsByWildcardPatterns", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Search scheme-related posts by government departments
     */
    public PaginatedResponse<PostResponse> searchSchemePostsByDepartment(User currentUser,
                                                                         Long beforePostId,
                                                                         Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            // Define patterns for scheme-related posts
            List<String> schemePatterns = List.of(
                    "#scheme%",
                    "#yojana%",
                    "%benefit%",
                    "#subsidy%",
                    "#government%",
                    "#welfare%"
            );

            return searchPostsByWildcardPatterns(currentUser, schemePatterns, Constant.ROLE_DEPARTMENT, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search scheme posts by department for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchSchemePostsByDepartment", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Search health-related posts by government departments
     */
    public PaginatedResponse<PostResponse> searchHealthPostsByDepartment(User currentUser,
                                                                         Long beforePostId,
                                                                         Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            // Define patterns for health-related posts
            List<String> healthPatterns = List.of(
                    "#health%",
                    "#medical%",
                    "#hospital%",
                    "#vaccine%",
                    "#ayushman%",
                    "#healthcare%"
            );

            return searchPostsByWildcardPatterns(currentUser, healthPatterns, Constant.ROLE_DEPARTMENT, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search health posts by department for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchHealthPostsByDepartment", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Search finance-related posts by government departments
     */
    public PaginatedResponse<PostResponse> searchFinancePostsByWildcard(User currentUser,
                                                                        Long beforePostId,
                                                                        Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            // Define patterns for finance-related posts
            List<String> financePatterns = List.of(
                    "#incometax%",
                    "#income%tax%",
                    "#newincomerule%",
                    "#tax%",
                    "#finance%",
                    "#gst%",
                    "#banking%",
                    "#loan%",
                    "#mudra%",
                    "#pmjdy%",
                    "#financial%",
                    "#budget%",
                    "#subsidy%",
                    "#taxrefund%",
                    "#taxfiling%",
                    "%income%tax%rule%",
                    "%new%income%rule%"
            );

            return searchPostsByWildcardPatterns(currentUser, financePatterns, Constant.ROLE_DEPARTMENT, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search finance posts by wildcard for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchFinancePostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Search education and employment-related posts by government departments
     */
    public PaginatedResponse<PostResponse> searchEducationEmploymentPostsByWildcard(User currentUser,
                                                                                    Long beforePostId,
                                                                                    Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            // Define patterns for education and employment-related posts
            List<String> educationEmploymentPatterns = List.of(
                    "#education%",
                    "#school%",
                    "#college%",
                    "#university%",
                    "#scholarship%",
                    "#digital%india%",
                    "#skill%development%",
                    "#employment%",
                    "#job%",
                    "#rojgar%",
                    "#startup%",
                    "#pmegp%",
                    "#self%employment%",
                    "#training%",
                    "#certification%",
                    "#upskilling%",
                    "#apprenticeship%",
                    "#pmkvy%",
                    "#ddugky%",
                    "#nrlm%",
                    "#mnrega%",
                    "#employment%guarantee%",
                    "#skill%india%",
                    "#vocational%",
                    "#technical%education%",
                    "#career%",
                    "%employment%",
                    "%education%",
                    "%skill%development%",
                    "%rojgar%",
                    "%training%",
                    "%scholarship%",
                    "%job%",
                    "%rozgar%guarantee%"
            );

            return searchPostsByWildcardPatterns(currentUser, educationEmploymentPatterns, Constant.ROLE_DEPARTMENT, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search education employment posts by wildcard for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchEducationEmploymentPostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }

    /**
     * Search infrastructure and transportation-related posts by government departments
     */
    public PaginatedResponse<PostResponse> searchInfrastructurePostsByWildcard(User currentUser,
                                                                               Long beforePostId,
                                                                               Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            // Define patterns for infrastructure and transportation-related posts
            List<String> infrastructurePatterns = List.of(
                    "#infrastructure%",
                    "#development%",
                    "#construction%",
                    "#highway%",
                    "#railway%",
                    "#transport%",
                    "#metro%",
                    "#bus%",
                    "#road%",
                    "#bridge%",
                    "#digital%india%",
                    "#broadband%",
                    "#internet%",
                    "#wifi%",
                    "#5g%",
                    "#smart%city%",
                    "#urban%",
                    "#housing%",
                    "#pmay%",
                    "#swachh%bharat%",
                    "#electricity%",
                    "#power%",
                    "#water%",
                    "#sanitation%",
                    "#connectivity%",
                    "#airport%",
                    "#port%",
                    "#logistics%",
                    "#renewable%energy%",
                    "#solar%",
                    "#clean%energy%",
                    "#green%",
                    "%infrastructure%",
                    "%development%",
                    "%transport%",
                    "%road%",
                    "%railway%",
                    "%digital%india%",
                    "%smart%city%",
                    "%housing%",
                    "%electricity%",
                    "%water%supply%"
            );

            return searchPostsByWildcardPatterns(currentUser, infrastructurePatterns, Constant.ROLE_DEPARTMENT, beforePostId, limit);

        } catch (Exception e) {
            log.error("Failed to search infrastructure posts by wildcard for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchInfrastructurePostsByWildcard", e, PaginationUtils.validateLimit(limit));
        }
    }
    /**
     * Search high priority emergency posts with specific hashtags
     * Only returns broadcasting posts that are visible to the user based on location
     */
    public PaginatedResponse<PostResponse> searchHighPriorityEmergencyPosts(User currentUser,
                                                                            Long beforePostId,
                                                                            Integer limit) {
        try {
            PostUtility.validateUser(currentUser);

            // Define high priority emergency patterns
            List<String> emergencyPatterns = List.of(
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

            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "searchHighPriorityEmergencyPosts", beforePostId, limit);

            // Build specification for emergency posts with broadcast scope only
            Specification<Post> spec = Specification.where(
                            PostSpecification.isBroadcastPost()
                    ).and(PostSpecification.isActiveStatus())
                    .and(PostSpecification.hasEmergencyHashtags(emergencyPatterns));

            // Add cursor-based pagination if beforePostId is provided
            if (setup.hasCursor()) {
                spec = spec.and((root, query, cb) -> cb.lessThan(root.get("id"), setup.getSanitizedCursor()));
            }

            // Add ordering by creation date (most recent first)
            spec = spec.and((root, query, cb) -> {
                query.orderBy(cb.desc(root.get("createdAt")));
                return cb.conjunction();
            });

            // Execute query
            Page<Post> postPage = postRepository.findAll(spec, setup.toPageable());
            List<Post> allPosts = postPage.getContent();

            // Filter posts based on user's location and broadcast visibility using PostUtility
            List<Post> visiblePosts = allPosts.stream()
                    .filter(post -> PostUtility.isPostVisibleToUser(post, currentUser))
                    .collect(Collectors.toList());

            // Convert to post responses
            List<PostResponse> postResponses = visiblePosts.stream()
                    .map(post -> postService.convertToPostResponse(post, currentUser))
                    .collect(Collectors.toList());

            // Create paginated response
            PaginatedResponse<PostResponse> response = PaginationUtils.createIdBasedResponse(
                    postResponses, setup.getValidatedLimit(), PostResponse::getId);

            PaginationUtils.logPaginationResults("searchHighPriorityEmergencyPosts", postResponses,
                    response.isHasMore(), response.getNextCursor());

            return response;

        } catch (Exception e) {
            log.error("Failed to search high priority emergency posts for user: {}",
                    currentUser != null ? currentUser.getActualUsername() : "null", e);
            return PaginationUtils.handlePaginationError("searchHighPriorityEmergencyPosts", e,
                    PaginationUtils.validateLimit(limit));
        }
    }
}