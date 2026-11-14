package com.zhengte.platform.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Enums {

    private Enums() {
    }

    public static <E extends Enum<E> & CodedEnum> Optional<E> find(Class<E> type, String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.code().equals(code))
                .findFirst();
    }

    public static <E extends Enum<E> & CodedEnum> List<String> codes(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(CodedEnum::code).collect(Collectors.toList());
    }
}
