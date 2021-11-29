package dstears.github.io.util.checker;

import dstears.github.io.util.checker.model.CheckerResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractChecker {

    protected final Map<String, List<CheckerResult>> errors = new HashMap<>();

    protected void putErrors(String key, List<CheckerResult> values) {
        List<CheckerResult> strings = errors.get(key);
        if (strings == null) {
            strings = new ArrayList<>();
        }
        strings.addAll(values);
        errors.put(key, strings);
    }
}
