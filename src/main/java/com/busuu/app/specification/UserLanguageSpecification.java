package com.busuu.app.specification;

import com.busuu.app.entities.Language;
import com.busuu.app.entities.UserLanguage;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.List;

public class UserLanguageSpecification
{
    public static Specification<UserLanguage> getSpecification(
            List<String> learning,
            List<String> speaking
    )
    {
        return (Root<UserLanguage> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            //Predicate act like a single condition
            List<Predicate> predicates = new ArrayList<>();

            //Joining
            Join<UserLanguage, Language> languageJoin = root.join("language", JoinType.LEFT);

            //Manual filter
            if (!learning.isEmpty())
            {
                for (String language : learning) predicates.add(cb.and
                        (
                            cb.equal(languageJoin.get("name"), language),
                            cb.equal(root.get("learningStatus"), "IN_PROGRESS")
                        ));
            }

            if (!speaking.isEmpty())
            {
                for (String language : learning) predicates.add(cb.and
                        (
                                cb.equal(languageJoin.get("name"), language),
                                cb.notEqual(root.get("speakingStatus"), "NO_PROFICIENCY")
                        ));
            }

            //Avoid duplicate case
            List<Order> orders = new ArrayList<>();
            orders.add(cb.asc(root.get("id")));
            query.orderBy(orders);

            //Criteria Builder (cb here) acting like a WHERE clause, which require predicate parameter is an Array of Predicate
            return cb.or(predicates.toArray(new Predicate[0]));

        };
    }

}
