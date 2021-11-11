package dstears.github.io.util.checker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 检查结果
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CheckerResult {
    private String key;
    private String message;
    private CheckerType checkerType;

    public CheckerResult(String message, CheckerType checkerType) {
        this.message = message;
        this.checkerType = checkerType;
    }
}
