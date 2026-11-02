package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation;

import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OopGrindingCompilerTest {

    private OopGrindingCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new OopGrindingCompiler();
    }

    @AfterEach
    void tearDown() {
        compiler.destroy();
    }

    @Test
    @DisplayName("Test 1: Successfully compile multiple Java files producing .class files in classes/")
    void compile_ValidMultiFileAcrossPackages_CompilesToClassesDir(@TempDir Path workDir) throws IOException {
        Path shape = workDir.resolve("Shape.java");
        Path circle = workDir.resolve("Circle.java");
        Path main = workDir.resolve("Main.java");

        Files.writeString(shape, "public interface Shape { double area(); }");
        Files.writeString(circle, "public class Circle implements Shape { private double r; public Circle(double r){this.r=r;} public double area(){return Math.PI*r*r;} }");
        Files.writeString(main, "public class Main { public static void main(String[] args){ Shape s = new Circle(10); } }");

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isTrue();
        assertThat(workDir.resolve("classes/Shape.class")).exists();
        assertThat(workDir.resolve("classes/Circle.class")).exists();
        assertThat(workDir.resolve("classes/Main.class")).exists();
    }

    @Test
    @DisplayName("Test 2: Compile classes in packages preserving directory structure inside classes/")
    void compile_PackageHierarchy_CompilesToNestedClassesDir(@TempDir Path workDir) throws IOException {
        Path modelDir = workDir.resolve("com/example/model");
        Path appDir = workDir.resolve("com/example");
        Files.createDirectories(modelDir);

        Files.writeString(modelDir.resolve("Animal.java"), "package com.example.model;\npublic class Animal {}");
        Files.writeString(appDir.resolve("Dog.java"), "package com.example;\nimport com.example.model.Animal;\npublic class Dog extends Animal {}");

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isTrue();
        assertThat(workDir.resolve("classes/com/example/model/Animal.class")).exists();
        assertThat(workDir.resolve("classes/com/example/Dog.class")).exists();
    }

    @Test
    @DisplayName("Test 3: Fail fast when syntax error occurs returning diagnostic error")
    void compile_SyntaxError_FailsFastWithDiagnostics(@TempDir Path workDir) throws IOException {
        Path broken = workDir.resolve("Broken.java");
        Files.writeString(broken, "public class Broken { int x = ; }");

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("error:");
        assertThat(workDir.resolve("classes/Broken.class")).doesNotExist();
    }

    @Test
    @DisplayName("Test 4: Fail gracefully when no Java files exist")
    void compile_EmptyDirectory_ReturnsFailure(@TempDir Path workDir) throws IOException {
        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("No Java source files found");
    }

    @Test
    @DisplayName("Test 5: Compile mutual circular dependencies across packages in one batch")
    void compile_CircularDependenciesBetweenPackages_CompilesSuccessfully(@TempDir Path workDir) throws IOException {
        Path pkgA = workDir.resolve("pkgA");
        Path pkgB = workDir.resolve("pkgB");
        Files.createDirectories(pkgA);
        Files.createDirectories(pkgB);

        Files.writeString(pkgA.resolve("ClassA.java"), """
            package pkgA;
            import pkgB.ClassB;
            public class ClassA {
                public ClassB getB() { return new ClassB(this); }
            }
            """);

        Files.writeString(pkgB.resolve("ClassB.java"), """
            package pkgB;
            import pkgA.ClassA;
            public class ClassB {
                private final ClassA a;
                public ClassB(ClassA a) { this.a = a; }
                public ClassA getA() { return a; }
            }
            """);

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isTrue();
        assertThat(workDir.resolve("classes/pkgA/ClassA.class")).exists();
        assertThat(workDir.resolve("classes/pkgB/ClassB.class")).exists();
    }
}
