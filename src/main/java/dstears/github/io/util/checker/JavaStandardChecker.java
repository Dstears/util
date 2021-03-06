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

                CheckerResult checkerResult = new CheckerResult("??????????????????", JavaStandardCheckerType.FILE);
                checkerResult.setKey(replace.substring(replace.indexOf("com" + System.getProperty("file.separator"))));
                putErrors(file, Collections.singletonList(checkerResult));
                continue;
            }

            JCTree.JCCompilationUnit jcCompilationUnit = parse.get();

            if (jcCompilationUnit.pid == null) {
                String replace = file.replace(filePath, "");
                putErrors(replace.substring(replace.indexOf("com" + System.getProperty("file.separator"))), Collections.singletonList(new CheckerResult("?????????????????????????????????????????????", JavaStandardCheckerType.FILE)));
                continue;
            }
            siduantongyiModel(file, jcCompilationUnit);
            siduantongyiMethod(file, jcCompilationUnit);


        }
        return errors;
    }


    /**
     * ?????????????????? controller??????
     * 1?????????????????????Abstract??????
     * 2??????Abstract???????????????????????????
     * 3????????????@Api??????
     * 4???api???????????????tags
     * 5??????????????????RequestMapping??????????????????GetMapping??????PostMapping
     * 6??????????????????ApiOperation??????
     * 7???mapping???????????????value
     * 8???ApiOperation???????????????value??????
     * 9???post???????????????????????????
     * 10???post????????????????????????dto
     * 11???get????????????????????????????????????
     * 12???get???????????????????????????dto??????????????????
     * 13???get?????????dto??????????????????????????????
     */
    private void siduantongyiMethod(String file, JCTree.JCCompilationUnit jcCompilationUnit) {
        List<CheckerResult> error = new ArrayList<>();
        String packageName = jcCompilationUnit.pid.toString();
        Matcher packageMatcher = CONTROLLER_PATTERN.matcher(packageName);
        if (packageMatcher.find()) {
            Optional<JCTree.JCClassDecl> classDecl = JavaParseUtil.getClassDecl(jcCompilationUnit);
            if (!classDecl.isPresent()) {
                error.add(new CheckerResult("???????????????body", JavaStandardCheckerType.FILE));
                return;
            }
            JCTree.JCClassDecl jcTree = classDecl.get();
            String className = jcTree.name.toString();
            String mods = jcTree.mods.toString();
            boolean isAbstract = mods.contains("abstract");
            if (isAbstract && !className.startsWith("Abstract")) {
                error.add(new CheckerResult("????????????????????????????????????Abstract??????", JavaStandardCheckerType.CLASS));
            }
            if (!isAbstract && className.startsWith("Abstract")) {
                error.add(new CheckerResult("?????????Abstract??????????????????????????????", JavaStandardCheckerType.CLASS));
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
                error.add(new CheckerResult("??????RequestMapping????????????????????????????????????????????????", JavaStandardCheckerType.METHOD_ANNOTATION));
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
                error.add(new CheckerResult("??????Api??????", JavaStandardCheckerType.CLASS_ANNOTATION));
            } else {
                Optional<String> tags = JavaParseUtil.getAssignValue("tags", api);
                if (!tags.isPresent()) {
                    error.add(new CheckerResult("api????????????tags??????", JavaStandardCheckerType.CLASS_ANNOTATION));
                } else {
                    if (apiValueList.contains(tags.get())) {
                        error.add(new CheckerResult("api?????????tags?????????" + unicodeToChinese(tags.get()), JavaStandardCheckerType.CLASS_ANNOTATION));
                    } else {
                        apiValueList.add(tags.get());
                    }
                }
            }

            for (JCTree.JCMethodDecl method : JavaParseUtil.getClassMethod(jcCompilationUnit)) {

                String methodName = method.name.toString();


                if (JavaParseUtil.getAnnotation("RequestMapping", method.mods).isPresent()) {
                    error.add(new CheckerResult(methodName + "???????????????RequestMapping??????", JavaStandardCheckerType.METHOD_ANNOTATION));
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
                            error.add(new CheckerResult(methodName + "mapping????????????value", JavaStandardCheckerType.METHOD_ANNOTATION));
                        } else {
                            Matcher matcher = URL_PATTERN.matcher(packageName);
                            if (!matcher.find()) {
                                error.add(new CheckerResult("???????????????", JavaStandardCheckerType.PACKAGE));
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
                                error.add(new CheckerResult(methodName + " url?????????", JavaStandardCheckerType.METHOD_ANNOTATION));
                            }


                        }
                        if (apiOperation == null) {
                            error.add(new CheckerResult(methodName + "??????ApiOperation??????", JavaStandardCheckerType.METHOD_ANNOTATION));
                        } else {
                            Optional<String> apiOperationValue = JavaParseUtil.getAssignValue("value", apiOperation);
                            if (!apiOperationValue.isPresent()) {
                                error.add(new CheckerResult(methodName + " ApiOperation????????????value", JavaStandardCheckerType.METHOD_ANNOTATION));
                            } else {
                                if (apiOperationValueList.contains(apiOperationValue.get())) {
                                    error.add(new CheckerResult(methodName + " ApiOperation?????????value???" + apiOperationValue.get() + "??????", JavaStandardCheckerType.METHOD_ANNOTATION));
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
                                    error.add(new CheckerResult(methodName + "post???????????????????????????", JavaStandardCheckerType.METHOD_ANNOTATION));
                                } else {
                                    JCTree.JCVariableDecl param = paramsList.get(0);
                                    if (!JavaParseUtil.getAnnotation("RequestBody", param.mods).isPresent()) {
                                        error.add(new CheckerResult(methodName + "post??????????????????RequestBody", JavaStandardCheckerType.METHOD_ANNOTATION));
                                    } else {
                                        checkDTO(error, methodName, param);
                                    }

                                }
                            } else {
                                boolean isPrimary = paramsList.stream().anyMatch(i -> PRIMARY_PARAM_TYPES.contains(((JCTree.JCIdent) i.vartype).name.toString()));

                                if (isPrimary) {
                                    if (paramsList.size() > 5) {
                                        error.add(new CheckerResult(methodName + "get????????????????????????", JavaStandardCheckerType.METHOD_PARAM));
                                    }
                                    if (paramsList.stream().anyMatch(i -> !PRIMARY_PARAM_TYPES.contains(((JCTree.JCIdent) i.vartype).name.toString()))) {
                                        error.add(new CheckerResult(methodName + "get?????????????????????DTO?????????????????????", JavaStandardCheckerType.METHOD_PARAM));
                                    }
                                    for (JCTree.JCVariableDecl param : paramsList) {
                                        String paramTypeName = ((JCTree.JCIdent) param.vartype).name.toString();
                                        Optional<JCTree.JCAnnotation> apiParamOptional = JavaParseUtil.getAnnotation("ApiParam", param.mods);
                                        if (!apiParamOptional.isPresent()) {
                                            error.add(new CheckerResult(methodName + "?????????????????? " + paramTypeName + " ??????ApiParam??????", JavaStandardCheckerType.METHOD_PARAM));
                                        } else {
                                            JCTree.JCAnnotation apiParam = apiParamOptional.get();
                                            if (!JavaParseUtil.getAssignValue("value", apiParam).isPresent()) {
                                                error.add(new CheckerResult(methodName + "?????????????????? " + paramTypeName + " ApiParam????????????value", JavaStandardCheckerType.METHOD_PARAM));
                                            }
                                            if (!JavaParseUtil.getAssignValue("example", apiParam).isPresent()) {
                                                error.add(new CheckerResult(methodName + "?????????????????? " + paramTypeName + " ApiParam????????????example", JavaStandardCheckerType.METHOD_PARAM));
                                            }
                                            if (!JavaParseUtil.getAssignValue("required", apiParam).isPresent()) {
                                                error.add(new CheckerResult(methodName + "?????????????????? " + paramTypeName + " ApiParam????????????required", JavaStandardCheckerType.METHOD_PARAM));
                                            }
                                        }

                                    }
                                } else {
                                    if (paramsList.size() != 1) {
                                        error.add(new CheckerResult(methodName + "get????????????????????????", JavaStandardCheckerType.METHOD_PARAM));
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
            error.add(new CheckerResult(methodName + " ????????????DTO", JavaStandardCheckerType.METHOD_PARAM));
        }
        if (!JavaParseUtil.getAnnotation("Valid", param.mods).isPresent()) {
            error.add(new CheckerResult(methodName + " ????????????Valid", JavaStandardCheckerType.METHOD_PARAM));
        }
    }

    /**
     * ?????????????????? xxo
     * 1???????????????
     * 2????????????????????????VO??????DTO
     * 3????????????????????????@ApiModel
     * 4???ApiModel?????????value??????
     * 5???ApiModel???Value??????
     * 6???????????????????????????private???
     * 7???????????????ApiModelProperty??????
     * 8????????????ApiModelProperty?????????value???example?????????dto????????????required??????
     * 9???dto required=true?????????NotNull????????????
     */
    private void siduantongyiModel(String file, JCTree.JCCompilationUnit jcCompilationUnit) {
        List<CheckerResult> error = new ArrayList<>();
        String packageName = jcCompilationUnit.pid.toString();
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(packageName);
        if (packageMatcher.find()) {
            // xxxx.frontapi.xxxx.model.xxxx????????????
            boolean isDTO;
            if (packageName.endsWith("dto")) {
                isDTO = true;
            } else if (packageName.endsWith("vo")) {
                isDTO = false;
            } else {
                error.add(new CheckerResult("???????????????", JavaStandardCheckerType.PACKAGE));
                return;
            }

            JCTree.JCClassDecl jcTree = (JCTree.JCClassDecl) jcCompilationUnit.defs.stream().filter(i -> i instanceof JCTree.JCClassDecl).findAny().get();

            String className = jcTree.name.toString();

            if (isDTO && !className.endsWith("DTO")) {
                error.add(new CheckerResult("dto???????????????DTO??????", JavaStandardCheckerType.CLASS));
            }
            if (!isDTO && !className.endsWith("VO")) {
                error.add(new CheckerResult("vo???????????????VO??????", JavaStandardCheckerType.CLASS));
            }

            JCTree.JCModifiers classPart = jcTree.mods;

            boolean isAbstract = classPart.toString().contains(" abstract ");

            Optional<JCTree.JCAnnotation> apiModelOptional = JavaParseUtil.getAnnotation("ApiModel", classPart);
            if (!isAbstract) {
                if (!apiModelOptional.isPresent()) {
                    // ????????????ApiModel?????????????????????????????????
                    error.add(new CheckerResult("???????????????????????????@ApiModel", JavaStandardCheckerType.CLASS_ANNOTATION));
                } else {
                    JCTree.JCAnnotation jcAnnotation = apiModelOptional.get();
                    Optional<String> assignValue = JavaParseUtil.getAssignValue("value", jcAnnotation);
                    if (!assignValue.isPresent()) {
                        error.add(new CheckerResult("ApiModel??????Value", JavaStandardCheckerType.CLASS_ANNOTATION));
                    } else {
                        if (apiModelValueList.contains(assignValue.get())) {
                            error.add(new CheckerResult("ApiModel???Value?????????" + unicodeToChinese(assignValue.get()), JavaStandardCheckerType.CLASS_ANNOTATION));
                        } else {
                            apiModelValueList.add(assignValue.get());
                        }
                    }
                }
            }

            List<JCTree.JCVariableDecl> fieldParts = jcTree.defs.stream().filter(i -> i instanceof JCTree.JCVariableDecl).map(i -> (JCTree.JCVariableDecl) i).collect(Collectors.toList());

            for (JCTree.JCVariableDecl fieldPart : fieldParts) {
                String fieldName = fieldPart.name.toString();
                String str = "?????????" + fieldName + " ";
                if (!fieldPart.mods.toString().contains("private")) {
                    error.add(new CheckerResult("???????????????private???", JavaStandardCheckerType.FIELD));
                }
                Optional<JCTree.JCAnnotation> apiModelPropertyOptional = JavaParseUtil.getAnnotation("ApiModelProperty", fieldPart.mods);
                if (!apiModelPropertyOptional.isPresent()) {
                    error.add(new CheckerResult(str + "??????ApiModelProperty??????", JavaStandardCheckerType.FIELD_ANNOTATION));
                } else {
                    JCTree.JCAnnotation apiModelProperty = apiModelPropertyOptional.get();
                    if (!JavaParseUtil.getAssignValue("value", apiModelProperty).isPresent()) {
                        error.add(new CheckerResult(str + " ApiModelProperty ??????value??????", JavaStandardCheckerType.FIELD_ANNOTATION));
                    }
                    if (!JavaParseUtil.getAssignValue("example", apiModelProperty).isPresent()) {
                        error.add(new CheckerResult(str + " ApiModelProperty ??????example??????", JavaStandardCheckerType.FIELD_ANNOTATION));
                    }
                    Optional<String> required = JavaParseUtil.getAssignValue("required", apiModelProperty);
                    if (!required.isPresent()) {
                        if (isDTO) {
                            error.add(new CheckerResult(str + " ApiModelProperty ??????required??????", JavaStandardCheckerType.FIELD_ANNOTATION));
                        }
                    } else {
                        if (Objects.equals(required.get(), "true") && isDTO) {
                            if (!JavaParseUtil.getAnnotation("NotNull", fieldPart.mods).isPresent()) {
                                error.add(new CheckerResult(str + " ??????????????????????????????NotNull??????", JavaStandardCheckerType.FIELD_ANNOTATION));
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
                // ???????????? \u4e00-\u9fa5 (??????)
                if (s.length() >= 4) {//????????????????????????????????????
                    String chinese = s.substring(0, 4);
                    try {
                        int chr = Integer.parseInt(chinese, 16);
                        boolean isChinese = isChinese((char) chr);
                        //??????????????????????????????  ???????????????
                        if (isChinese) {//??????????????????
                            // ?????????string
                            string.append((char) chr);
                            //????????????  ???????????????
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
