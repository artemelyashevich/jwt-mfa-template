package com.elyashevich.jwt_2fa_template.repository.specification;

import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import org.springframework.data.jpa.domain.Specification;

public final class UserProfileSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserProfileSpecifications() {
        throw new UnsupportedOperationException();
    }

    /**
     * Case-insensitive substring match on email. {@code %} and {@code _} in the input are matched literally.
     */
    public static Specification<UserProfile> emailContains(String emailPart) {
        String pattern = "%" + escapeLike(emailPart.toLowerCase()) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(UserProfile.Fields.email)), pattern, LIKE_ESCAPE);
    }

    private static String escapeLike(String value) {
        return value
            .replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
            .replace("%", LIKE_ESCAPE + "%")
            .replace("_", LIKE_ESCAPE + "_");
    }
}
