package com.redhat.jhalliday;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.redhat.jhalliday.impl.*;
import com.redhat.jhalliday.impl.javaparser.CompilationUnitCreationTransformerFunction;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JarProcessor<LOW_AGGREGATE, LOW_ITEM> {

    private final TransformerFunction<Map<String, byte[]>, Map<String, ClassWrapper<LOW_AGGREGATE>>> classParsingFunction;

    private final MethodAssociatingRecordTransformer<ClassWrapper<LOW_AGGREGATE>, CompilationUnit, LOW_ITEM, MethodDeclaration> methodAssociatingRecordTransformer;

    public JarProcessor(
            TransformerFunction<Map<String, byte[]>, Map<String, ClassWrapper<LOW_AGGREGATE>>> classParsingFunction,
            MethodAssociatingRecordTransformer<ClassWrapper<LOW_AGGREGATE>, CompilationUnit, LOW_ITEM, MethodDeclaration> methodAssociatingRecordTransformer) {
        this.classParsingFunction = classParsingFunction;
        this.methodAssociatingRecordTransformer = methodAssociatingRecordTransformer;
    }

    public List<DecompilationRecord<ClassWrapper<LOW_AGGREGATE>, CompilationUnit>> associateFiles(
            DecompilationRecord<File, File> decompilationRecord) {

        List<DecompilationRecord<File, File>> jarRecords = List.of(decompilationRecord);

        /*
         * jar pairs are a good start, but next we need to extract the contents.
         * Note we extract to memory not disk, so it's byte[] not File result
         */
        JarContentRecordTransformer jarContentTransformer = new JarContentRecordTransformer(
                new JarContentTransformerFunction(s -> s.endsWith(".class")),
                new JarContentTransformerFunction(s -> s.endsWith(".java"))
        );
        List<DecompilationRecord<Map<String, byte[]>, Map<String, byte[]>>> rawFileContentRecords =
                jarRecords.stream().flatMap(jarContentTransformer).collect(Collectors.toList());

        /*
         * From this point we are decoupled from the filesystem
         * Next we need to convert the raw bytes to something more useful
         * Start with the low level (.class) side
         */
        CompositeRecordTransformer<Map<String, byte[]>, Map<String, byte[]>, Map<String, ClassWrapper<LOW_AGGREGATE>>, Map<String, byte[]>>
                ctClassBuildingTransformer = new CompositeRecordTransformer<>(
                classParsingFunction,
                new IdentityTransformerFunction<>()
        );
        List<DecompilationRecord<Map<String, ClassWrapper<LOW_AGGREGATE>>, Map<String, byte[]>>> semiParsed =
                rawFileContentRecords.stream().flatMap(ctClassBuildingTransformer).collect(Collectors.toList());

        /*
         * Now the high level side.
         * Note that this ordering will parse some .java files unnecessarily, as they won't have matching .class files in the next step.
         * However, it's cheaper than re-parsing the same .java file multiple times if it contains multiple classes.
         */
        CompositeRecordTransformer<Map<String, ClassWrapper<LOW_AGGREGATE>>, Map<String, byte[]>, Map<String, ClassWrapper<LOW_AGGREGATE>>, Map<String, CompilationUnit>>
                compilationUnitBuildingTransformer = new CompositeRecordTransformer<>(
                new IdentityTransformerFunction<>(),
                new CompilationUnitCreationTransformerFunction()
        );
        List<DecompilationRecord<Map<String, ClassWrapper<LOW_AGGREGATE>>, Map<String, CompilationUnit>>> fullyParsed =
                semiParsed.stream().flatMap(compilationUnitBuildingTransformer).collect(Collectors.toList());

        /*
         * Now we can pair up the individual .class and .java files, using the source file name from the .class
         */
        FileNameAssociatingRecordTransformer<ClassWrapper<LOW_AGGREGATE>, CompilationUnit> fileNameAssociatingRecordTransformer =
                new FileNameAssociatingRecordTransformer<>();
        List<DecompilationRecord<ClassWrapper<LOW_AGGREGATE>, CompilationUnit>> associatedFileRecords =
                fullyParsed.stream().flatMap(fileNameAssociatingRecordTransformer).collect(Collectors.toList());

        return associatedFileRecords;
    }

    /*
     * Decompose the file pairs into a number of Method pairs, ignoring the ones that are not translatable
     */
    public List<DecompilationRecord<LOW_ITEM, MethodDeclaration>> associateMethods(
            List<DecompilationRecord<ClassWrapper<LOW_AGGREGATE>, CompilationUnit>> associatedFileRecords) {

        List<DecompilationRecord<LOW_ITEM, MethodDeclaration>> methodRecords =
                associatedFileRecords.stream().flatMap(methodAssociatingRecordTransformer).collect(Collectors.toList());

        return methodRecords;
    }
}