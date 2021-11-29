package dstears.github.io.util.checker;

import com.sun.tools.javac.tree.JCTree;
import dstears.github.io.util.checker.model.CheckerResult;
import dstears.github.io.util.checker.model.JavaStandardCheckerType;
import dstears.github.io.util.common.FileUtil;
import dstears.github.io.util.common.JavaParseUtil;
import dstears.github.io.util.common.RequestUrlUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JavaStandardChecker extends AbstractChecker {


    private final Pattern PACKAGE_PATTERN = Pattern.compile(".*frontapi[^;]*model.*");
    private final Pattern CONTROLLER_PATTERN = Pattern.compile(".*frontapi[^;]*controller.*");
    private final Pattern URL_PATTERN = Pattern.compile("(?<=frontapi.)[a-z]*(?=.controller)");

    private final List<String> apiModelValueList = new ArrayList<>();
    private final List<String> apiValueList = new ArrayList<>();
    private final List<String> apiOperationValueList = new ArrayList<>();

    private final List<String> EXCLUDE_PARAM_TYPES = Arrays.asList("HttpServletRequest", "HttpServletResponse", "MultipartFile");
    private final List<String> PRIMARY_PARAM_TYPES = Arrays.asList(
            "Byte",
            "Short",
            "Integer",
            "Long",
            "Float",
            "Double",
            "Boolean",
            "Character",
            "String");

    public Map<String, List<CheckerResult>> run(String filePath) {

        List<String> files = FileUtil.listFile(filePath);
        files = files
                .stream()
                .filter(i -> i.endsWith(".java")).collect(Collectors.toList());
        for (String file : files) {
            Optional<JCTree.JCCompilationUnit> parse = JavaParseUtil.parse(file);

            if (!parse.isPresent()) {
                String replace = file.replace(filePath, "");

                CheckerResult checkerResult = new CheckerResult("解析文件失败", JavaStandardCheckerType.FILE);
                checkerResult.setKey(replace.substring(replace.indexOf("com" + System.getProperty("file.separator"))));
                putErrors(file, Collections.singletonList(checkerResult));
                continue;
            }

            JCTree.JCCompilationUnit jcCompilationUnit = parse.get();

            if (jcCompilationUnit.pid == null) {
                String replace = file.replace(filePath, "");
                putErrors(replace.substring(replace.indexOf("com" + System.getProperty("file.separator"))), Collections.singletonList(new CheckerResult("文件被整个注释掉了！！！请删除", JavaStandardCheckerType.FILE)));
                continue;
            }
            siduantongyiModel(file, jcCompilationUnit);
            siduantongyiMethod(file, jcCompilationUnit);


        }
        return errors;
    }


    /**
     * 四端统一规范 controller方法
     * 1、静态类必须以Abstract开头
     * 2、以Abstract开头的必须是静态类
     * 3、必须有@Api注解
     * 4、api注解必须有tags
     * 5、方法不能用RequestMapping注解，必须是GetMapping或者PostMapping
     * 6、方法必须有ApiOperation注解
     * 7、mapping注解必须有value
     * 8、ApiOperation注解必须有value字段
     * 9、post方法只能有一个入参
     * 10、post方法的入参必须是dto
     * 11、get方法最多五个基本类型入参
     * 12、get方法入参基本类型和dto不能同时存在
     * 13、get方法是dto的时候只能有一个入参
     */
    private void siduantongyiMethod(String file, JCTree.JCCompilationUnit jcCompilationUnit) {
        List<CheckerResult> error = new ArrayList<>();
        String packageName = jcCompilationUnit.pid.toString();
        Matcher packageMatcher = CONTROLLER_PATTERN.matcher(packageName);
        if (packageMatcher.find()) {
            Optional<JCTree.JCClassDecl> classDecl = JavaParseUtil.getClassDecl(jcCompilationUnit);
            if (!classDecl.isPresent()) {
                error.add(new CheckerResult("找不到类的body", JavaStandardCheckerType.FILE));
                return;
            }
            JCTree.JCClassDecl jcTree = classDecl.get();
            String className = jcTree.name.toString();
            String mods = jcTree.mods.toString();
            boolean isAbstract = mods.contains("abstract");
            if (isAbstract && !className.startsWith("Abstract")) {
                error.add(new CheckerResult("是静态类，但是类名不是以Abstract开头", JavaStandardCheckerType.CLASS));
            }
            if (!isAbstract && className.startsWith("Abstract")) {
                error.add(new CheckerResult("类名以Abstract开头，但是不是静态类", JavaStandardCheckerType.CLASS));
            }

            JCTree.JCAnnotation requestMapping = null;
            JCTree.JCAnnotation api = null;

            for (JCTree.JCAnnotation annotation : jcTree.mods.annotations) {
                if (Objects.equals(annotation.annotationType.toString(), "RequestMapping")) {
                    requestMapping = annotation;
                }
                if (Objects.equals(annotation.annotationType.toString(), "Api")) {
                    api = annotation;
                }
            }

            if (requestMapping == null) {
                error.add(new CheckerResult("没有RequestMapping注解，这怎么运行起来的？？？？？", JavaStandardCheckerType.METHOD_ANNOTATION));
                return;
            }
            Optional<String> value = JavaParseUtil.getAssignValue("value", requestMapping);
            String requestMappingValue;
            if (value.isPresent()) {
                requestMappingValue = RequestUrlUtil.format(value.get());
            } else {
                requestMappingValue = "";
            }

            if (api == null) {
                error.add(new CheckerResult("没有Api注解", JavaStandardCheckerType.CLASS_ANNOTATION));
            } else {
                Optional<String> tags = JavaParseUtil.getAssignValue("tags", api);
                if (!tags.isPresent()) {
                    error.add(new CheckerResult("api注解没有tags属性", JavaStandardCheckerType.CLASS_ANNOTATION));
                } else {
                    if (apiValueList.contains(tags.get())) {
                        error.add(new CheckerResult("api注解的tags重复：" + unicodeToChinese(tags.get()), JavaStandardCheckerType.CLASS_ANNOTATION));
                    } else {
                        apiValueList.add(tags.get());
                    }
                }
            }

            for (JCTree.JCMethodDecl method : JavaParseUtil.getClassMethod(jcCompilationUnit)) {

                String methodName = method.name.toString();


                if (JavaParseUtil.getAnnotation("RequestMapping", method.mods).isPresent()) {
                    error.add(new CheckerResult(methodName + "不允许使用RequestMapping注解", JavaStandardCheckerType.METHOD_ANNOTATION));
                } else {
                    JCTree.JCAnnotation mapping = null;
                    JCTree.JCAnnotation apiOperation = null;

                    for (JCTree.JCAnnotation annotation : method.mods.annotations) {
                        if (Arrays.asList("GetMapping", "PostMapping").contains(annotation.annotationType.toString())) {
                            mapping = annotation;
                        }

                        if (Objects.equals(annotation.annotationType.toString(), "ApiOperation")) {
                            apiOperation = annotation;
                        }
                    }
                    boolean isPost;
                    if (mapping != null) {
                        isPost = mapping.annotationType.toString().equals("PostMapping");

                        Optional<String> urlPre = JavaParseUtil.getAssignValue("value", mapping);

                        if (!urlPre.isPresent()) {
                            error.add(new CheckerResult(methodName + "mapping注解没有value", JavaStandardCheckerType.METHOD_ANNOTATION));
                        } else {
                            Matcher matcher = URL_PATTERN.matcher(packageName);
                            if (!matcher.find()) {
                                error.add(new CheckerResult("包名不正确", JavaStandardCheckerType.PACKAGE));
                            }
                            String urlP = matcher.group();
                            boolean hasMatch = false;
                            for (String s : urlPre.get().split(",")) {
                                s = RequestUrlUtil.format(s);
                                String fullUrl = requestMappingValue + s;
                                if (fullUrl.contains("/" + urlP + "/")) {
                                    hasMatch = true;
                                    break;
                                }

                            }


                            if (!hasMatch) {
                                error.add(new CheckerResult(methodName + " url不正确", JavaStandardCheckerType.METHOD_ANNOTATION));
                            }


                        }
                        if (apiOperation == null) {
                            error.add(new CheckerResult(methodName + "没有ApiOperation注解", JavaStandardCheckerType.METHOD_ANNOTATION));
                        } else {
                            Optional<String> apiOperationValue = JavaParseUtil.getAssignValue("value", apiOperation);
                            if (!apiOperationValue.isPresent()) {
                                error.add(new CheckerResult(methodName + " ApiOperation注解没有value", JavaStandardCheckerType.METHOD_ANNOTATION));
                            } else {
                                if (apiOperationValueList.contains(apiOperationValue.get())) {
                                    error.add(new CheckerResult(methodName + " ApiOperation注解的value：" + apiOperationValue.get() + "重复", JavaStandardCheckerType.METHOD_ANNOTATION));
                                } else {
                                    apiOperationValueList.add(apiOperationValue.get());
                                }
                            }
                        }

                        List<JCTree.JCVariableDecl> paramsList = method
                                .params
                                .stream()
                                .filter(i -> !EXCLUDE_PARAM_TYPES
                                        .contains(((JCTree.JCIdent) i.vartype).name.toString())).collect(Collectors.toList());
                        if (!paramsList.isEmpty()) {
                            if (isPost) {
                                if (paramsList.size() != 1) {
                                    error.add(new CheckerResult(methodName + "post方法只能有一个参数", JavaStandardCheckerType.METHOD_ANNOTATION));
                                } else {
                                    JCTree.JCVariableDecl param = paramsList.get(0);
                                    if (!JavaParseUtil.getAnnotation("RequestBody", param.mods).isPresent()) {
                                        error.add(new CheckerResult(methodName + "post方法入参没有RequestBody", JavaStandardCheckerType.METHOD_ANNOTATION));
                                    } else {
                                        checkDTO(error, methodName, param);
                                    }

                                }
                            } else {
                                boolean isPrimary = paramsList.stream().anyMatch(i -> PRIMARY_PARAM_TYPES.contains(((JCTree.JCIdent) i.vartype).name.toString()));

                                if (isPrimary) {
                                    if (paramsList.size() > 5) {
                                        error.add(new CheckerResult(methodName + "get方法最多五个入参", JavaStandardCheckerType.METHOD_PARAM));
                                    }
                                    if (paramsList.stream().anyMatch(i -> !PRIMARY_PARAM_TYPES.contains(((JCTree.JCIdent) i.vartype).name.toString()))) {
                                        error.add(new CheckerResult(methodName + "get方法不能同时有DTO和基本类的入参", JavaStandardCheckerType.METHOD_PARAM));
                                    }
                                    for (JCTree.JCVariableDecl param : paramsList) {
                                        String paramTypeName = ((JCTree.JCIdent) param.vartype).name.toString();
                                        Optional<JCTree.JCAnnotation> apiParamOptional = JavaParseUtil.getAnnotation("ApiParam", param.mods);
                                        if (!apiParamOptional.isPresent()) {
                                            error.add(new CheckerResult(methodName + "基本类型入参 " + paramTypeName + " 没有ApiParam注解", JavaStandardCheckerType.METHOD_PARAM));
                                        } else {
                                            JCTree.JCAnnotation apiParam = apiParamOptional.get();
                                            if (!JavaParseUtil.getAssignValue("value", apiParam).isPresent()) {
                                                error.add(new CheckerResult(methodName + "基本类型入参 " + paramTypeName + " ApiParam注解没有value", JavaStandardCheckerType.METHOD_PARAM));
                                            }
                                            if (!JavaParseUtil.getAssignValue("example", apiParam).isPresent()) {
                                                error.add(new CheckerResult(methodName + "基本类型入参 " + paramTypeName + " ApiParam注解没有example", JavaStandardCheckerType.METHOD_PARAM));
                                            }
                                            if (!JavaParseUtil.getAssignValue("required", apiParam).isPresent()) {
                                                error.add(new CheckerResult(methodName + "基本类型入参 " + paramTypeName + " ApiParam注解没有required", JavaStandardCheckerType.METHOD_PARAM));
                                            }
                                        }

                                    }
                                } else {
                                    if (paramsList.size() != 1) {
                                        error.add(new CheckerResult(methodName + "get方法最多五个入参", JavaStandardCheckerType.METHOD_PARAM));
                                    } else {
                                        JCTree.JCVariableDecl param = paramsList.get(0);
                                        checkDTO(error, methodName, param);
                                    }

                                }
                            }
                        }

                    }
                }
            }

            if (error.size() > 0) {
                error.forEach(i -> i.setKey(packageName + "." + className));
                putErrors(file, error);
            }
        }
    }

    private void checkDTO(List<CheckerResult> error, String methodName, JCTree.JCVariableDecl param) {
        String paramTypeName = ((JCTree.JCIdent) param.vartype).name.toString();
        if (!paramTypeName.endsWith("DTO")) {
            error.add(new CheckerResult(methodName + " 入参不是DTO", JavaStandardCheckerType.METHOD_PARAM));
        }
        if (!JavaParseUtil.getAnnotation("Valid", param.mods).isPresent()) {
            error.add(new CheckerResult(methodName + " 入参没有Valid", JavaStandardCheckerType.METHOD_PARAM));
        }
    }

    /**
     * 四端统一规范 xxo
     * 1、包名校验
     * 2、类名校验是否带VO或者DTO
     * 3、非静态类必须带@ApiModel
     * 4、ApiModel必须有value字段
     * 5、ApiModel的Value重复
     * 6、实体类字段必须是private的
     * 7、字段没有ApiModelProperty注解
     * 8、字段的ApiModelProperty必须有value，example属性，dto的必须有required属性
     * 9、dto required=true必须有NotNull注解限定
     */
    private void siduantongyiModel(String file, JCTree.JCCompilationUnit jcCompilationUnit) {
        List<CheckerResult> error = new ArrayList<>();
        String packageName = jcCompilationUnit.pid.toString();
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(packageName);
        if (packageMatcher.find()) {
            // xxxx.frontapi.xxxx.model.xxxx包下的类
            boolean isDTO;
            if (packageName.endsWith("dto")) {
                isDTO = true;
            } else if (packageName.endsWith("vo")) {
                isDTO = false;
            } else {
                error.add(new CheckerResult("包名不正确", JavaStandardCheckerType.PACKAGE));
                return;
            }

            JCTree.JCClassDecl jcTree = (JCTree.JCClassDecl) jcCompilationUnit.defs.stream().filter(i -> i instanceof JCTree.JCClassDecl).findAny().get();

            String className = jcTree.name.toString();

            if (isDTO && !className.endsWith("DTO")) {
                error.add(new CheckerResult("dto包的类不是DTO结尾", JavaStandardCheckerType.CLASS));
            }
            if (!isDTO && !className.endsWith("VO")) {
                error.add(new CheckerResult("vo包的类不是VO结尾", JavaStandardCheckerType.CLASS));
            }

            JCTree.JCModifiers classPart = jcTree.mods;

            boolean isAbstract = classPart.toString().contains(" abstract ");

            Optional<JCTree.JCAnnotation> apiModelOptional = JavaParseUtil.getAnnotation("ApiModel", classPart);
            if (!isAbstract) {
                if (!apiModelOptional.isPresent()) {
                    // 如果没有ApiModel并且不是静态类，则异常
                    error.add(new CheckerResult("不是静态类，且没有@ApiModel", JavaStandardCheckerType.CLASS_ANNOTATION));
                } else {
                    JCTree.JCAnnotation jcAnnotation = apiModelOptional.get();
                    Optional<String> assignValue = JavaParseUtil.getAssignValue("value", jcAnnotation);
                    if (!assignValue.isPresent()) {
                        error.add(new CheckerResult("ApiModel没有Value", JavaStandardCheckerType.CLASS_ANNOTATION));
                    } else {
                        if (apiModelValueList.contains(assignValue.get())) {
                            error.add(new CheckerResult("ApiModel的Value重复：" + unicodeToChinese(assignValue.get()), JavaStandardCheckerType.CLASS_ANNOTATION));
                        } else {
                            apiModelValueList.add(assignValue.get());
                        }
                    }
                }
            }

            List<JCTree.JCVariableDecl> fieldParts = jcTree.defs.stream().filter(i -> i instanceof JCTree.JCVariableDecl).map(i -> (JCTree.JCVariableDecl) i).collect(Collectors.toList());

            for (JCTree.JCVariableDecl fieldPart : fieldParts) {
                String fieldName = fieldPart.name.toString();
                String str = "字段：" + fieldName + " ";
                if (!fieldPart.mods.toString().contains("private")) {
                    error.add(new CheckerResult("类字段不是private的", JavaStandardCheckerType.FIELD));
                }
                Optional<JCTree.JCAnnotation> apiModelPropertyOptional = JavaParseUtil.getAnnotation("ApiModelProperty", fieldPart.mods);
                if (!apiModelPropertyOptional.isPresent()) {
                    error.add(new CheckerResult(str + "没有ApiModelProperty注解", JavaStandardCheckerType.FIELD_ANNOTATION));
                } else {
                    JCTree.JCAnnotation apiModelProperty = apiModelPropertyOptional.get();
                    if (!JavaParseUtil.getAssignValue("value", apiModelProperty).isPresent()) {
                        error.add(new CheckerResult(str + " ApiModelProperty 没有value属性", JavaStandardCheckerType.FIELD_ANNOTATION));
                    }
                    if (!JavaParseUtil.getAssignValue("example", apiModelProperty).isPresent()) {
                        error.add(new CheckerResult(str + " ApiModelProperty 没有example属性", JavaStandardCheckerType.FIELD_ANNOTATION));
                    }
                    Optional<String> required = JavaParseUtil.getAssignValue("required", apiModelProperty);
                    if (!required.isPresent()) {
                        if (isDTO) {
                            error.add(new CheckerResult(str + " ApiModelProperty 没有required属性", JavaStandardCheckerType.FIELD_ANNOTATION));
                        }
                    } else {
                        if (Objects.equals(required.get(), "true") && isDTO) {
                            if (!JavaParseUtil.getAnnotation("NotNull", fieldPart.mods).isPresent()) {
                                error.add(new CheckerResult(str + " 是必填字段，但是没有NotNull注解", JavaStandardCheckerType.FIELD_ANNOTATION));
                            }
                        }
                    }


                }
            }

            if (error.size() > 0) {
                error.forEach(i -> i.setKey(packageName + "." + className));
                putErrors(file, error);
            }
        }
    }

    private String unicodeToChinese(final String unicode) {
        StringBuilder string = new StringBuilder();
        String[] hex = unicode.split("\\\\u");
        for (String s : hex) {
            try {
                // 汉字范围 \u4e00-\u9fa5 (中文)
                if (s.length() >= 4) {//取前四个，判断是否是汉字
                    String chinese = s.substring(0, 4);
                    try {
                        int chr = Integer.parseInt(chinese, 16);
                        boolean isChinese = isChinese((char) chr);
                        //转化成功，判断是否在  汉字范围内
                        if (isChinese) {//在汉字范围内
                            // 追加成string
                            string.append((char) chr);
                            //并且追加  后面的字符
                            String behindString = s.substring(4);
                            string.append(behindString);
                        } else {
                            string.append(s);
                        }
                    } catch (NumberFormatException e1) {
                        string.append(s);
                    }
                } else {
                    string.append(s);
                }
            } catch (NumberFormatException e) {
                string.append(s);
            }
        }
        return string.toString();
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }


}
