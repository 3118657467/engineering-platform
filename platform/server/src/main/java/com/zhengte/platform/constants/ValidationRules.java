package com.zhengte.platform.constants;

import java.util.regex.Pattern;

public final class ValidationRules {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final Pattern BRANCH_SUFFIX = Pattern.compile("[a-z0-9][a-z0-9-]{0,49}");
    public static final Pattern BRANCH_NAME = Pattern.compile("[A-Za-z0-9._/-]{1,120}");
    public static final Pattern PROJECT_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,49}");

    private ValidationRules() {
    }
}
