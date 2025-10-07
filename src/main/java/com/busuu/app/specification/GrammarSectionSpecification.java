package com.busuu.app.specification;

import com.busuu.app.entities.Grammar;
import com.busuu.app.entities.GrammarSection;
import com.busuu.app.entities.Lesson;
import com.busuu.app.entities.Level;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrammarSectionSpecification
{

    //For List<String> filter only
    //private static final Set<String> FILTER_FIELDS = Set.of("grammarId", "lessonId", "levelId");
    private static final Set<String> SORT_FIELDS = Set.of("title", "description" , "content", "grammarSectionOrder", "grammarTitle", "lessonTitle", "levelCode" , "createdAt", "updatedAt");

    public static Specification<GrammarSection> getSpecification(
            String searchValue,
            String grammar,
            String lesson,
            String level,
            List<String> sortBy,
            List<String> sortDirection
    )
    {
        return (Root<GrammarSection> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            //Predicate act like a single condition
            List<Predicate> predicates = new ArrayList<>();

            //Joining
            Join<GrammarSection, Grammar> grammarJoin = root.join("grammar", JoinType.LEFT);
            Join<GrammarSection, Lesson> lessonJoin = root.join("lesson", JoinType.LEFT);
            Join<GrammarSection, Level> levelJoin = root.join("level", JoinType.LEFT);

            //Filter then search then sort

            //Filter

            //Filter for List<String>
//            if (filterBy != null && filterValue != null)
//            {
//                if (filterBy.size() != filterValue.size()) throw new IllegalArgumentException("filterBy and filterValue must have the same number of elements");
//
//                for (int i = 0; i < filterBy.size(); i++)
//                {
//                    String column = filterBy.get(i);
//                    String value = filterValue.get(i);
//
//                    if (!FILTER_FIELDS.contains(column)) {
//                        throw new IllegalArgumentException("Unsupported filter column: " + column + "; Support filter by: " + FILTER_FIELDS);
//                    }
//
//                    switch (column)
//                    {
//                        case "grammarId":
//                            predicates.add(cb.equal(cb.lower(grammarJoin.get("id")), value.toLowerCase()));
//                            break;
//                        case "lessonId":
//                            predicates.add(cb.equal(cb.lower(lessonJoin.get("id")), value.toLowerCase()));
//                            break;
//                        case "levelId":
//                            predicates.add(cb.equal(cb.lower(levelJoin.get("id")), value.toLowerCase()));
//                            break;
//
//                    }
//                }
//            }

            //Manual filter
            if ( grammar != null && !grammar.isEmpty() ) predicates.add(cb.equal(cb.lower(grammarJoin.get("id")), grammar));
            if ( lesson != null && !lesson.isEmpty() ) predicates.add(cb.equal(cb.lower(lessonJoin.get("id")), lesson));
            if ( level != null && !level.isEmpty() ) predicates.add(cb.equal(cb.lower(levelJoin.get("id")), level));


            //Global search (LIKE SEARCH)
            if (searchValue != null && !searchValue.isEmpty())
            {

                String val;
                boolean isDateInput = false;

                //Process for date-time and date input

                String dateTimeRegex = "^([01]?[0-9]|2[0-3]):([0-5]?[0-9])\\s([0-2]?[0-9]|3[01])/(0[1-9]|1[0-2])/([0-9]{4})$";
                String dateRegex = "^([0-2]?[0-9]|3[01])/(0[1-9]|1[0-2])/([0-9]{4})$";


                // Create a pattern and matcher
                Pattern dateTimePattern = Pattern.compile(dateTimeRegex);
                Matcher dateTimeMatcher = dateTimePattern.matcher(searchValue);

                Pattern datePattern = Pattern.compile(dateRegex);
                Matcher dateMatcher = datePattern.matcher(searchValue);

                //For search not exact (cb.like)
                if (dateMatcher.matches()) isDateInput = true;
                val = "%" + searchValue.toLowerCase() + "%";

                //For search exact (cb.equal)
                //String val = searchValue.toLowerCase();

                Predicate createdAtPredicate = null;
                Predicate updatedAtPredicate = null;

                if (isDateInput)
                {
                    LocalDateTime[] dateRange = formatDateToRange(searchValue);
                    createdAtPredicate = cb.between(root.get("createdAt"), dateRange[0], dateRange[1]);
                    updatedAtPredicate = cb.between(root.get("updatedAt"), dateRange[0], dateRange[1]);

                }
                else if (dateTimeMatcher.matches())
                {
                    LocalDateTime[] dateTimeRange = formatDateTime(searchValue);
                    createdAtPredicate = cb.between(root.get("createdAt"), dateTimeRange[0], dateTimeRange[1]);
                    updatedAtPredicate = cb.between(root.get("updatedAt"), dateTimeRange[0], dateTimeRange[1]);

                }

                Predicate titlePredicate = cb.like(cb.lower(root.get("title")), val);
                Predicate descriptionPredicate = cb.like(cb.toString(root.get("description")), val);
                Predicate contentPredicate = cb.like(cb.toString(root.get("content")), val);
                Predicate grammarSectionOrderPredicate = cb.like(cb.toString(root.get("grammarSectionOrder")), val);
                Predicate grammarTitlePredicate = cb.like(cb.lower(grammarJoin.get("title")), val);
                Predicate lessonTitlePredicate = cb.like(cb.lower(lessonJoin.get("title")), val);
                Predicate levelCodePredicate = cb.like(cb.lower(levelJoin.get("code")), val);

                predicates.add(cb.or(
                        createdAtPredicate,
                        updatedAtPredicate,
                        titlePredicate,
                        descriptionPredicate,
                        contentPredicate,
                        grammarSectionOrderPredicate,
                        grammarTitlePredicate,
                        lessonTitlePredicate,
                        levelCodePredicate));

            }

            //Sorting
            List<Order> orders = new ArrayList<>();

            if (sortBy != null && !sortBy.isEmpty())
            {
                for (int i = 0; i < sortBy.size(); i++)
                {
                    String sortColumn = sortBy.get(i);

                    String direction = "asc";
                    if ( sortDirection != null && i < sortDirection.size()) direction = sortDirection.get(i);

                    if (!direction.equals("asc") && !direction.equals("desc")) {
                        throw new IllegalArgumentException("Unsupported sort direction: " + direction + "; Support sort by: asc, desc");
                    }

                    if (!SORT_FIELDS.contains(sortColumn)) {
                        throw new IllegalArgumentException("Unsupported sort column: " + sortColumn + "; Support filter by: " + SORT_FIELDS);
                    }

                    switch (sortColumn)
                    {

                        case "title":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(root.get("title"))
                                    : cb.desc(root.get("title")));
                            break;
                        case "description":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(root.get("description"))
                                    : cb.desc(root.get("description")));
                            break;
                        case "content":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(root.get("content"))
                                    : cb.desc(root.get("content")));
                            break;
                        case "grammarSectionOrder":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(cb.toString(root.get("grammarSectionOrder")))
                                    : cb.desc(cb.toString(root.get("grammarSectionOrder"))));
                            break;
                        case "grammarTitle":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(cb.toString(grammarJoin.get("title")))
                                    : cb.desc(cb.toString(grammarJoin.get("title"))));
                            break;
                        case "lessonTitle":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(cb.toString(lessonJoin.get("title")))
                                    : cb.desc(cb.toString(lessonJoin.get("title"))));
                            break;
                        case "levelCode":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(cb.toString(levelJoin.get("code")))
                                    : cb.desc(cb.toString(levelJoin.get("code"))));
                            break;
                        case "createdAt":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(root.get("createdAt"))
                                    : cb.desc(root.get("createdAt")));
                            break;
                        case "updatedAt":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(root.get("updatedAt"))
                                    : cb.desc(root.get("updatedAt")));
                            break;

                    }
                }
            }
            else //Default sort
            {

                orders.add(cb.asc(grammarJoin.get("title")));
                orders.add(cb.asc(levelJoin.get("code")));
                orders.add(cb.asc(root.get("grammarSectionOrder")));
                orders.add(cb.asc(lessonJoin.get("title")));

            }

            //Avoid duplicate case
            orders.add(cb.asc(root.get("id")));

            query.orderBy(orders);

            //Criteria Builder (cb here) acting like a WHERE clause, which require predicate parameter is an Array of Predicate
            return cb.and(predicates.toArray(new Predicate[0]));

        };
    }

    public static LocalDateTime[] formatDateTime(String userInput)
    {

        //The input format the user gives
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        LocalDateTime dateTime = LocalDateTime.parse(userInput, inputFormatter);

        //This LocalDateTime is already fixed by itself -7
        LocalDateTime start = dateTime.minusMinutes(dateTime.getMinute()).withSecond(0);
        LocalDateTime end = dateTime.plusMinutes(59 - dateTime.getMinute()).withSecond(59);

        return new LocalDateTime[]{start, end};

    }

    public static LocalDateTime[] formatDateToRange(String userInput)
    {

        //The input format the user gives
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate date = LocalDate.parse(userInput, inputFormatter);

        //This LocalDateTime is already fixed by itself -7
        LocalDateTime start = date.minusDays(1).atTime(23, 59,0);
        LocalDateTime end = date.atTime(23, 59, 59);

        return new LocalDateTime[]{start, end};

    }
}
