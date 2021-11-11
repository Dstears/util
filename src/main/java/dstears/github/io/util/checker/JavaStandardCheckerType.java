package dstears.github.io.util.checker;

public enum JavaStandardCheckerType implements CheckerType {

    /**
     * 校验类型枚举
     */
    FILE(100, "文件内容"),
    PACKAGE(101, "包"),
    CLASS(102, "类定义"),
    METHOD_ANNOTATION(103, "方法注解"),
    FIELD(107, "字段定义"),
    CLASS_ANNOTATION(104, "类注解"),
    FIELD_ANNOTATION(106, "字段注解"),
    METHOD_PARAM(105, "方法入参");

    private Integer type;
    private String name;

    JavaStandardCheckerType(int type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }
}
