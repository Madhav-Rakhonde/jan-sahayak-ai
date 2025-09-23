package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.ArrayList;

public class PostSpecification {

    public static Specification<Post> hasWildcardHashtags(List<String> patterns, String roleName) {
        return (root, query, criteriaBuilder) -> {

            // This list will hold all our individual LIKE conditions
            List<Predicate> predicates = new ArrayList<>();

            // Create a LIKE predicate for each pattern in the list and add it
            for (String pattern : patterns) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("content")), pattern.toLowerCase()));
            }

            // Combine all the LIKE predicates with OR
            Predicate hashtagsPredicate = criteriaBuilder.or(predicates.toArray(new Predicate[0]));

            // Create predicates for the other fixed conditions
            Predicate statusPredicate = criteriaBuilder.equal(root.get("status"), com.JanSahayak.AI.enums.PostStatus.ACTIVE);
            Predicate rolePredicate = criteriaBuilder.equal(root.get("user").get("role").get("name"), roleName);
            Predicate userActivePredicate = criteriaBuilder.isTrue(root.get("user").get("isActive"));

            // Add explicit ordering by createdAt DESC to show recently posted posts first
            query.orderBy(criteriaBuilder.desc(root.get("createdAt")));

            // Combine all conditions with AND
            return criteriaBuilder.and(statusPredicate, rolePredicate, userActivePredicate, hashtagsPredicate);
        };
    }
}