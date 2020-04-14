package ncl.ac.uk.matcher;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.util.Printer;
import jdk.internal.org.objectweb.asm.util.Textifier;
import jdk.internal.org.objectweb.asm.util.TraceMethodVisitor;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

//TODO: modify this class to be immutable

/**
 * This class handles the connection between a library's source codes
 * and bytecode. Basically, this class creates the connection between
 * .class files and related .java files.
 */
public class ASMClassJavaPair implements ClassJavaPair {

    /* ************************************************
     * ************************************************
     * *************** Class's Fields *****************
     * ************************************************
     * ************************************************/

    // Path of the JAR that contains .class files
    private final String _binJARFullPath;
    // Path of the JAR that contains .java files
    private final String _srcJARFullPath;
    // Map that stores connections between .class and .java files
    private final Map<String, List<ClassNode>> _pairs;

    /* ************************************************
     * ************************************************
     * ***************** Constructors *****************
     * ************************************************
     * ************************************************/

    public ASMClassJavaPair(String binJARFullPath, String srcJARFullPath) throws IOException {

        String[] splitArray = binJARFullPath.split("/");
        String binJARFilename = splitArray[splitArray.length - 1];

        // Check if the path binJARFullPath ends with a .jar file
        if (!binJARFilename.endsWith(".jar"))
            throw new IllegalArgumentException(String.format("%s is not a .jar file.", binJARFilename));

        // Check if the two paths (e.g. binJARFullPath and srcJARFullPath) are related to the same library name
        if (!srcJARFullPath.contains(binJARFilename.substring(0, binJARFilename.length() - ".jar".length())))
            throw new IllegalArgumentException("Wrong source library path.");

        this._binJARFullPath = binJARFullPath;
        this._srcJARFullPath = srcJARFullPath;

        this._pairs = asmUtility.extractClassNodesFromJAR(binJARFullPath, srcJARFullPath);
    }

    /* ************************************************
     * ************************************************
     * **************** Public APIs *******************
     * ************************************************
     * ************************************************/

    /**
     * This method creates a List of DecompilationRecord where each record is a pair of
     * Bytecode and related source code.
     *
     * @param dotJavaFilename .java file to analyse
     * @return List of DecompilationRecord
     */
    @Override
    public List<DecompilationRecord> getDecompilationRecords(String dotJavaFilename) throws IOException {

        List<DecompilationRecord> records = new LinkedList<>();

        // Checks if the parameter dotJavaFile matches a .java file in this object
        if (this._pairs.containsKey(dotJavaFilename)) {

            String sourceCode = JARUtility.extractSourceCode(this._srcJARFullPath, dotJavaFilename);

            // TODO: substitute dotJavaFile with the actual file.
            records = asmUtility.extractDecompilationRecordsFromDotClass(sourceCode, this._pairs.get(dotJavaFilename));
        }

        return records;
    }

    @Override
    public List<Sentence> getSentences(String dotJavaFile) throws Exception {
        return null;
    }

    @Override
    public List<String> getMethodsNames(String dotJavaFile) {

        List<String> methodsNames = new LinkedList<>();

        if (this._pairs.containsKey(dotJavaFile)) {
            for (ClassNode classNode : this._pairs.get(dotJavaFile))
                for (MethodNode method : classNode.methods)
                    methodsNames.add(String.format("%s/%s", classNode.name, method.name));
        }

        return methodsNames;
    }

    /* ************************************************
     * ************************************************
     * **************** GETTER/SETTER *****************
     * ************************************************
     * ************************************************/

    @Override
    public String getSrcPath() { return this._srcJARFullPath; }

    @Override
    public String getBinPath() { return this._binJARFullPath; }

    @Override
    public String getSourceJARFile() {
        String[] splitArray = this._srcJARFullPath.split("/");
        return splitArray[splitArray.length - 1];
    }

    @Override
    public List<String> getDotJavaFiles() { return new LinkedList<>(this._pairs.keySet()); }

    /* ************************************************
     * ************************************************
     * *************** Utility Classes ****************
     * ************************************************
     * ************************************************/

    private static class asmUtility {

        private static Printer printer = new Textifier();
        private static TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(printer);

        /**
         * This private method creates a List of .class files and corresponding .java files
         *
         * @param binJarPath path of the JAR file that contains .class files
         * @return Map of String and InputStream corresponding, respectively, to .java and .class files
         */
        private static Map<String, List<ClassNode>> extractClassNodesFromJAR(String binJarPath, String srcJarPath) throws IOException {

            // HashMap to insert .class files that matches .java files
            Map<String, List<ClassNode>> dotClassFiles = new HashMap<>();

            // List all .java files in the source .jar file
            List<String> dotJavaFiles = JARUtility.ListDotJavaFiles(srcJarPath);

            // Read the JAR file that contains .class files
            JarFile jarFile = new JarFile(new File(binJarPath));

            for (JarEntry jarEntry : Collections.list(jarFile.entries())) {
                String name = jarEntry.getName();
                // The second condition could be excluded if module-info is removed from jar files
                // directly through the POM file. There are plugins (such as AntRun) that can do that but I
                // wasted too much time trying to understand how to configure the plugin to run in the package
                // phase. TODO: ask Jonathan to help with this.
                if (name.endsWith(".class") &&
                        !name.startsWith("module-info")) {
                    // extracts the InputStream corresponding to the current .class
                    InputStream dotClass = jarFile.getInputStream(jarEntry);
                    // fetches the .java file corresponding to the .class file (if there is one)
                    ClassNode classNode = convertToClassNode(dotClass);

                    String sourceFile = classNode.sourceFile;
                    // Checks if there is a .java file matching the source filename

                    // Commented out because of Jonathan's advice
                    //if (dotJavaFiles.contains(sourceFile)) {
                    if (dotJavaFiles.stream().anyMatch(x -> x.contains(sourceFile))) {

                        // Update or create List of ClassNode
                        List<ClassNode> classNodes = dotClassFiles.getOrDefault(sourceFile, null);
                        if (classNodes == null)
                            classNodes = new LinkedList<>();

                        classNodes.add(classNode);

                        dotClassFiles.put(sourceFile, classNodes);
                    }
                }

            }

            return dotClassFiles;
        }

        /**
         * This private method convert an InputStream to a ClassNode.
         *
         * @param dotClassFile This input parameter is the InputStream containing the .class file
         * @return a new ClassNode object extracted from the .class file
         */
        private static ClassNode convertToClassNode(InputStream dotClassFile) throws IOException {

            ClassReader reader = new ClassReader(dotClassFile);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            return classNode;
        }

        /**
         * This private method extracts DecompilationRecord(s) from a .class file
         *
         * Partially based on;
         * https://stackoverflow.com/questions/19152526/asm-outputting-java-bytecode-and-opcode
         *
         * @param sourceFile .java source code
         * @param classNodes List of ClassNode representing the .class matching .java
         * @return List of DecompilationRecord
         */
        private static List<DecompilationRecord> extractDecompilationRecordsFromDotClass(String sourceFile, List<ClassNode> classNodes) {

            // For each class in the collection of classes in dotJavaFile
            for (ClassNode classNode : classNodes)
                // For each method in the class
                for (MethodNode method : classNode.methods) {
                    // Fetch all instructions in the method (List of AbstractInsnNode)
                    InsnList nodes = method.instructions;
                    for(int i = 0; i < nodes.size(); i++) {
                        //TODO: understand what kind of connection we want to achieve between bytecode and instructions
                        insnToString(nodes.get(i));
                    }
                }

            return new LinkedList<>();
        }

        private static String insnToString(AbstractInsnNode insn){
            insn.accept(traceMethodVisitor);
            StringWriter sw = new StringWriter();
            printer.print(new PrintWriter(sw));
            printer.getText().clear();
            return sw.toString();
        }
    }

    /**
     * Utility class used by the outer-class to analyse JAR files. The inner-class is declared
     * private because ClassJavaPair is designed to be the only class to interact with JAR files.
     */
    private static class JARUtility {

        /**
         * This method lists all .java files in a JAR file
         *
         * @param jarPath path of the JAR file to analyse
         * @return A list of Strings that contains all .java filenames found in the JAR
         */
        private static List<String> ListDotJavaFiles(String jarPath) throws IOException {

            List<String> dotJavaFiles = ListFilesInJAR(jarPath, "java");

            /**
             * This was commented out on the advice of Jonathan.
             * TODO: ask Jonathan if in the same JAR there can be same .java files
             *
            // This little modification was added because ClassNode holds only the filename
            // of the .java file and not the entire package path.
            dotJavaFiles.replaceAll(x -> {
                String[] tempArray = x.split("/");
                return tempArray[tempArray.length - 1];
            });
            */

            return dotJavaFiles;
        }

        /**
         * This private method reads all files in a JAR file
         *
         * @param jarFullPath path of the JAR file to analyse
         * @param extension   This is the extension the method will focus on
         * @return List of the files read
         */
        private static List<String> ListFilesInJAR(String jarFullPath, String extension) throws IOException {

            List<String> dotClassFiles = new LinkedList<>();

            JarFile jarFile = new JarFile(new File(jarFullPath));

            for (JarEntry jarEntry : Collections.list(jarFile.entries())) {
                String name = jarEntry.getName();
                if (name.endsWith(String.format(".%s", extension)))
                    dotClassFiles.add(name);
            }

            return dotClassFiles;
        }

        private static String extractSourceCode(String srcJarPath, String dotJavaFilename) throws IOException {

            // Read the JAR file that contains .class files
            JarFile jarFile = new JarFile(new File(srcJarPath));

            List<String> dotJavaFiles = ListDotJavaFiles(srcJarPath);
            String completeDotJavaFilename = dotJavaFiles.stream().filter(x -> x.contains(dotJavaFilename)).collect(toSingleton());
            JarEntry jarEntry = jarFile.getJarEntry(completeDotJavaFilename);

            InputStream dotJavaFile = jarFile.getInputStream(jarEntry);

            return "";
        }

        /**
         * https://stackoverflow.com/questions/22694884/filter-java-stream-to-1-and-only-1-element
         * @param <T> Generic
         * @return a collector to use in a Stream
         */
        private static <T> Collector<T, ?, T> toSingleton() {
            return Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                        if (list.size() != 1) {
                            throw new IllegalStateException();
                        }
                        return list.get(0);
                    }
            );
        }
    }

}
