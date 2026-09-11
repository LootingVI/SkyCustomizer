package de.flori.skycustomizer.util;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.tools.*;

public final class SourceUsageChecks {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null);
             var paths = Files.walk(Path.of("src"))) {
            var sources = paths.filter(p -> p.toString().endsWith(".java")).toList();
            var task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-implicit:none", "-classpath", System.getProperty("java.class.path")),
                    null, files.getJavaFileObjectsFromPaths(sources));
            var units = task.parse();
            task.analyze();
            var errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
            if (!errors.isEmpty()) throw new AssertionError(errors.toString());
            var trees = Trees.instance(task);
            Map<Element, String> declarations = new LinkedHashMap<>();
            Set<Element> referenced = new HashSet<>();
            Set<Element> read = new HashSet<>();
            Set<String> reflected = new HashSet<>();
            for (var unit : units) new TreePathScanner<Void, Void>() {
                final boolean production = unit.getSourceFile().getName().replace('\\', '/').contains("src/main/java/");
                void declare(Tree tree) {
                    Element element = trees.getElement(getCurrentPath());
                    if (production && element != null) declarations.put(element,
                            unit.getSourceFile().getName() + ":" + unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, tree)));
                }
                @Override public Void visitMethod(MethodTree node, Void ignored) {
                    if (!node.getName().contentEquals("<init>") && node.getModifiers().getAnnotations().isEmpty()) declare(node);
                    return super.visitMethod(node, ignored);
                }
                @Override public Void visitVariable(VariableTree node, Void ignored) {
                    Element element = trees.getElement(getCurrentPath());
                    if (element != null && element.getKind() == ElementKind.FIELD
                            && (!(element.getEnclosingElement() instanceof TypeElement type)
                            || type.getRecordComponents().stream().noneMatch(c -> c.getSimpleName().equals(element.getSimpleName())))) declare(node);
                    return super.visitVariable(node, ignored);
                }
                @Override public Void visitImport(ImportTree node, Void ignored) { return null; }
                @Override public Void visitLiteral(LiteralTree node, Void ignored) {
                    if (node.getValue() instanceof String value) reflected.addAll(List.of(value.split("[,|]")));
                    return super.visitLiteral(node, ignored);
                }
                void reference() {
                    Element element = trees.getElement(getCurrentPath());
                    referenced.add(element);
                    Tree parent = getCurrentPath().getParentPath().getLeaf();
                    if (!(parent instanceof AssignmentTree assignment && assignment.getVariable() == getCurrentPath().getLeaf())) read.add(element);
                }
                @Override public Void visitIdentifier(IdentifierTree node, Void ignored) { reference(); return super.visitIdentifier(node, ignored); }
                @Override public Void visitMemberSelect(MemberSelectTree node, Void ignored) { reference(); return super.visitMemberSelect(node, ignored); }
                @Override public Void visitMemberReference(MemberReferenceTree node, Void ignored) { reference(); return super.visitMemberReference(node, ignored); }
            }.scan(unit, null);
            int unused = 0;
            for (var entry : declarations.entrySet()) {
                Element element = entry.getKey();
                if (reflected.contains(element.getSimpleName().toString())) continue;
                if (!(element.getKind() == ElementKind.FIELD ? read : referenced).contains(element)) {
                    System.out.println(entry.getValue() + " " + element.getEnclosingElement() + "." + element);
                    unused++;
                }
            }
            System.out.println("Source usage: " + unused + " unreferenced members.");
            if (unused > 0 && !Arrays.asList(args).contains("--report")) throw new AssertionError("Unreferenced production members: " + unused);
        }
    }
}
