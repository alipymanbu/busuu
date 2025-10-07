package com.busuu.app.specification;

import com.busuu.app.entities.Course;
import com.busuu.app.entities.CourseLevel;
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

public class CourseSpecification
{

    //For List<String> filter only
    //private static final Set<String> FILTER_FIELDS = Set.of("levelId");
    private static final Set<String> SORT_FIELDS = Set.of("title", "description", "courseOrder", "createdAt", "updatedAt");

    public static Specification<Course> getSpecification(
            String searchValue,
            String level,
            String language,
            List<String> sortBy,
            List<String> sortDirection
    )
    {
        return (Root<Course> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
        {

            //Predicate act like a single condition
            List<Predicate> predicates = new ArrayList<>();

            //Joining
            Join<Course, CourseLevel> courseLevelJoin = root.join("courseLevels", JoinType.LEFT);
            Join<CourseLevel, Level> levelJoin = courseLevelJoin.join("level", JoinType.LEFT);

            //Filter then search then sort

            //Filter
            //Filter with List<String>
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
//                        case "levelId":
//                            predicates.add(cb.equal(levelJoin.get("id"), value.toLowerCase()));
//                            break;
//
//                    }
//                }
//            }

            //Manual filter

            if (language != null && !language.isEmpty()) {
                predicates.add(cb.equal(root.get("language").get("id"), language));
            }

            if (level != null && !level.isEmpty() ) predicates.add(cb.equal(levelJoin.get("id"), level));

            //Global search (LIKE SEARCH)
            if (searchValue != null && !searchValue.isEmpty())
            {

                String val = "%" + searchValue.toLowerCase() + "%"; //For search not exact (cb.like)

                //For search exact (cb.equal)
                //String val = searchValue.toLowerCase();

                boolean isDateInput = false;
                boolean isDateTimeInput = false;
                boolean isLevelCodeInput = false;

                //Process for date-time and date input

                String dateTimeRegex = "^([01]?[0-9]|2[0-3]):([0-5]?[0-9])\\s([0-2]?[0-9]|3[01])/(0[1-9]|1[0-2])/([0-9]{4})$";
                String dateRegex = "^([0-2]?[0-9]|3[01])/(0[1-9]|1[0-2])/([0-9]{4})$";
                String levelCodeRegex = "^(A1|A2|B1|B2|C1)(\\s*,\\s*(A1|A2|B1|B2|C1))*$";

                // Create a pattern and matcher
                Pattern dateTimePattern = Pattern.compile(dateTimeRegex);
                Matcher dateTimeMatcher = dateTimePattern.matcher(searchValue);

                Pattern datePattern = Pattern.compile(dateRegex);
                Matcher dateMatcher = datePattern.matcher(searchValue);

                Pattern levelCodePattern = Pattern.compile(levelCodeRegex);
                Matcher levelCodeMatcher = levelCodePattern.matcher(searchValue);

                if (dateTimeMatcher.matches()) isDateTimeInput = true;
                else if (dateMatcher.matches()) isDateInput = true;
                else if (levelCodeMatcher.matches()) isLevelCodeInput = true;

                Predicate createdAtPredicate;
                Predicate updatedAtPredicate;

                if (isDateInput)
                {

                    LocalDateTime[] dateRange = formatDateToRange(searchValue);
                    createdAtPredicate = cb.between(root.get("createdAt"), dateRange[0], dateRange[1]);
                    updatedAtPredicate = cb.between(root.get("updatedAt"), dateRange[0], dateRange[1]);

                    Predicate titlePredicate = cb.like(cb.lower(root.get("title")), val);
                    Predicate descriptionPredicate = cb.like(cb.lower(root.get("description")), val);
                    Predicate courseOrderPredicate = cb.like(cb.toString(root.get("courseOrder")), val);

                    predicates.add(cb.or(
                            createdAtPredicate,
                            updatedAtPredicate,
                            titlePredicate,
                            descriptionPredicate,
                            courseOrderPredicate));

                }
                else if (isDateTimeInput)
                {

                    LocalDateTime[] dateTimeRange = formatDateTime(searchValue);
                    createdAtPredicate = cb.between(root.get("createdAt"), dateTimeRange[0], dateTimeRange[1]);
                    updatedAtPredicate = cb.between(root.get("updatedAt"), dateTimeRange[0], dateTimeRange[1]);

                    Predicate titlePredicate = cb.like(cb.lower(root.get("title")), val);
                    Predicate descriptionPredicate = cb.like(cb.lower(root.get("description")), val);
                    Predicate courseOrderPredicate = cb.like(cb.toString(root.get("courseOrder")), val);

                    predicates.add(cb.or(
                            createdAtPredicate,
                            updatedAtPredicate,
                            titlePredicate,
                            descriptionPredicate,
                            courseOrderPredicate));

                }
                else if (isLevelCodeInput)
                {

                    String[] levelCodes = searchValue.split("\\s*,\\s*");

                    //Add IN predicate: WHERE code IN ( '...' , '...' ,... )
                    //No use "=" combine with AND because it cannot be like WHERE code = "A1" AND code = "A2"
                    //Cannot equal to 2 value at the same time in course_level table
                    predicates.add(levelJoin.get("code").in((Object[]) levelCodes));

                    //Group by course ID: GROUP BY id
                    query.groupBy(root.get("id"));

                    //Only include courses that match ALL level codes: HAVING COUNT (DISTINCT code) = ... ;
                    query.having(cb.equal(cb.countDistinct(levelJoin.get("code")), levelCodes.length));

                }
                else
                {

                    Predicate titlePredicate = cb.like(cb.lower(root.get("title")), val);
                    Predicate descriptionPredicate = cb.like(cb.lower(root.get("description")), val);
                    Predicate courseOrderPredicate = cb.like(cb.toString(root.get("courseOrder")), val);

                    predicates.add(cb.or(
                            titlePredicate,
                            descriptionPredicate,
                            courseOrderPredicate));

                }

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
                                    ? cb.asc(cb.toString(root.get("description")))
                                    : cb.desc(cb.toString(root.get("description"))));
                            break;
                        case "courseOrder":
                            orders.add(direction.equalsIgnoreCase("asc")
                                    ? cb.asc(root.get("courseOrder"))
                                    : cb.desc(root.get("courseOrder")));
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
                orders.add(cb.asc(root.get("courseOrder")));
            }

            orders.add(cb.asc(root.get("id")));

            //Avoid multiple record
            query.distinct(true);

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
