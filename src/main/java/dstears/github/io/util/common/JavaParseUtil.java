package dstears.github.io.util.common;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class JavaParseUtil {

    private static final ParserFactory PARSER_FACTORY;
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParseUtil.class);

    static {
        Context context = new Context();
        JavacFileManager.preRegister(context);
        PARSER_FACTORY = ParserFactory.instance(context);
    }

    private JavaParseUtil() {
    }

    /**
     * 从类或者字段或者方法上获取某个注解
     *
     * @param name
     * @param classPart
     * @return
     */
    public static Optional<JCTree.JCAnnotation> getAnnotation(String name, JCTree.JCModifiers classPart) {
        return classPart.annotations.stream().filter(i -> Objects.equals(i.annotationType.toString(), name)).findAny();
    }

    /**
     * 从注解上获取某个属性的值
     *
     * @param key        属性名
     * @param annotation 注解类对象
     * @return 注解的值
     */
    public static Optional<String> getAssignValue(String key, JCTree.JCAnnotation annotation) {
        for (JCTree.JCExpression arg : annotation.args) {
            if (arg instanceof JCTree.JCLiteral) {
                if ("value".equals(key)) {
                    return Optional.of(((JCTree.JCLiteral) arg).value.toString());
                }
            } else if (arg instanceof JCTree.JCNewArray && "value".equals(key)) {
                JCTree.JCNewArray jcNewArray = (JCTree.JCNewArray) arg;
                return Optional.of(jcNewArray.elems.stream().map(i -> ((JCTree.JCLiteral) i).value.toString()).collect(Collectors.joining(",")));
            } else {
                JCTree.JCAssign jcAssign = (JCTree.JCAssign) arg;
                if (Objects.equals(key, ((JCTree.JCIdent) jcAssign.lhs).name.toString())) {
                    if (jcAssign.rhs instanceof JCTree.JCNewArray) {
                        JCTree.JCNewArray jcNewArray = (JCTree.JCNewArray) jcAssign.rhs;
                        return Optional.of(jcNewArray.elems.stream().map(i -> ((JCTree.JCLiteral) i).value.toString()).collect(Collectors.joining(",")));
                    } else {
                        return Optional.of(((JCTree.JCLiteral) jcAssign.rhs).value.toString());
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<JCTree.JCClassDecl> getClassDecl(JCTree.JCCompilationUnit jcCompilationUnit) {
        return jcCompilationUnit.defs.stream().filter(i -> i instanceof JCTree.JCClassDecl).map(i -> (JCTree.JCClassDecl) i).findAny();

    }

    public static List<JCTree.JCMethodDecl> getClassMethod(JCTree.JCCompilationUnit jcCompilationUnit) {
        return getClassDecl(jcCompilationUnit).map(jcClassDecl -> jcClassDecl.defs.stream().filter(i -> i instanceof JCTree.JCMethodDecl).map(i -> (JCTree.JCMethodDecl) i).collect(Collectors.toList())).orElse(Collections.emptyList());

    }

    public static Optional<JCTree.JCCompilationUnit> parse(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {

            StringBuilder builder = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                builder.append(s).append("\n");
            }
            String fileContent = builder.toString();

            JavacParser javacParser = PARSER_FACTORY.newParser(fileContent, true, false, true);

            return Optional.ofNullable(javacParser.parseCompilationUnit());

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return Optional.empty();
        }
    }
}
