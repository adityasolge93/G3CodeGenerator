package com.gogettergeeks.processor;

import com.gogettergeeks.annotation.FileDBGenerated;
import com.gogettergeeks.annotation.Persisted;
import com.gogettergeeks.annotation.UniqueKey;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("com.gogettergeeks.annotation.FileDBGenerated")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class FileDBProcessor extends AbstractProcessor {
    private static final List<String> EXCEPTION_CLASS_NAMES = new ArrayList<>() {{
        add("RequestException");
        add("ServiceException");
    }};
    private static final String DTO_SUFFIX = "GeneratedDto";

    private String packageName;
    private String className;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean isClaimed = false;
        Set<? extends Element> fileDbGeneratedAnnotatedClasses = roundEnv.getElementsAnnotatedWith(FileDBGenerated.class);
        for (Element element : fileDbGeneratedAnnotatedClasses) {
            if (element.getKind() == ElementKind.CLASS) {
                TypeElement classElement = (TypeElement) element;

                List<VariableElement> fields = new ArrayList<>();
                List<VariableElement> uniqueKeyFields = new ArrayList<>();
                for (Element enclosedElement : classElement.getEnclosedElements()) {
                    if (enclosedElement.getKind() == ElementKind.FIELD
                            && enclosedElement.getAnnotation(UniqueKey.class) != null) {
                        VariableElement fieldElement = (VariableElement) enclosedElement;
                        uniqueKeyFields.add(fieldElement);
                    } else if (enclosedElement.getKind() == ElementKind.FIELD
                            && enclosedElement.getAnnotation(Persisted.class) != null) {
                        VariableElement fieldElement = (VariableElement) enclosedElement;
                        fields.add(fieldElement);
                    }
                }

                if (!fields.isEmpty()) {
                    TypeElement enclosingClass = (TypeElement) fields.stream().findAny().get().getEnclosingElement();
                    this.packageName = processingEnv.getElementUtils().getPackageOf(enclosingClass).toString();
                    this.className = enclosingClass.getSimpleName().toString();
                    generateModel(fields, uniqueKeyFields);
                    generateExceptionClasses();
                    generateInterface();
                    generateDao(fields, uniqueKeyFields);
                }
            }
        }

        return isClaimed;
    }

    private void generateModel(List<VariableElement> fields, List<VariableElement> uniqueKeyFields) {
        StringBuilder model = new StringBuilder();
        String generatedClassName = this.className + DTO_SUFFIX;
        model.append("package ").append(this.packageName).append(";\n\n");
        model.append("import java.io.Serializable;\n\n");
        model.append("public class ").append(generatedClassName).append(" implements Serializable {\n\n");
        model.append("private static final long serialVersionUID = 1L;\n\n");

        for (Element uniqueKeyField : uniqueKeyFields) {
            String fieldName = uniqueKeyField.getSimpleName().toString();
            String fieldType = uniqueKeyField.asType().toString();
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            model.append(String.format("\tprivate %s %s;\n\n",
                    fieldType, fieldName));
            model.append(String.format("\tpublic void %s(%s %s) {\n\t\tthis.%s = %s;\n\t}\n\n",
                    setterName, fieldType, fieldName, fieldName, fieldName));
            model.append(String.format("\tpublic %s %s() { \n\t\treturn this.%s;\n\t}\n\n",
                    fieldType, getterName, fieldName));
        }

        for (Element field : fields) {
            String fieldName = field.getSimpleName().toString();
            String fieldType = field.asType().toString();
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            model.append(String.format("\tprivate %s %s;\n\n",
                    fieldType, fieldName));
            model.append(String.format("\tpublic void %s(%s %s) {\n\t\tthis.%s = %s;\n\t}\n\n",
                    setterName, fieldType, fieldName, fieldName, fieldName));
            model.append(String.format("\tpublic %s %s() { \n\t\treturn this.%s;\n\t}\n\n",
                    fieldType, getterName, fieldName));
        }

        model.append("}\n");

        try {
            Writer writer = processingEnv.getFiler()
                    .createSourceFile(this.packageName + "." + generatedClassName)
                    .openWriter();
            writer.write(model.toString());
            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void generateExceptionClasses() {
        for (String exceptionClassName : EXCEPTION_CLASS_NAMES) {
            StringBuilder body = new StringBuilder();
            body.append("package ").append(this.packageName).append(";\n\n");
            body.append("public class ").append(this.className).append(exceptionClassName).append(" extends Exception {\n");
            body.append("   private String message;\n\n");
            body.append("   public ").append(this.className).append(exceptionClassName).append("(String message) {\n");
            body.append("       this.message = message;\n");
            body.append("   }\n\n");
            body.append("   public String getMessage() {\n");
            body.append("       return this.message;\n");
            body.append("   }\n\n");
            body.append("   public String toString() {\n");
            body.append("       return this.getMessage();\n");
            body.append("   }\n");
            body.append("}\n");

            try {
                Writer writer = processingEnv.getFiler()
                        .createSourceFile(this.packageName + "." + this.className + exceptionClassName)
                        .openWriter();
                writer.write(body.toString());
                writer.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private void generateInterface() {
        StringBuilder throwsExceptionString = new StringBuilder();
        for (int i=0; i < EXCEPTION_CLASS_NAMES.size(); i++) {
            throwsExceptionString.append(this.className).append(EXCEPTION_CLASS_NAMES.get(i));
            if (i != EXCEPTION_CLASS_NAMES.size()-1) {
                throwsExceptionString.append(", ");
            }
        }

        StringBuilder body = new StringBuilder();
        body.append("package ").append(this.packageName).append(";\n\n");
        for (String exception : EXCEPTION_CLASS_NAMES) {
            body.append("import ").append(this.packageName).append(".")
                    .append(this.className).append(exception).append(";\n");
        }
        body.append("\nimport java.util.List;\n\n");
        body.append("public interface ").append(this.className).append("Dao").append(" {\n");
        body.append("   void add(").append(this.className).append(" ")
                .append(this.className.toLowerCase()).append(") throws ").append(throwsExceptionString)
                .append(";\n");
        body.append("   void delete(").append(this.className).append(" ")
                .append(this.className.toLowerCase()).append(") throws ").append(throwsExceptionString)
                .append(";\n");
        body.append("   void update(").append(this.className).append(" ")
                .append(this.className.toLowerCase()).append(") throws ").append(throwsExceptionString)
                .append(";\n");
        body.append("   List<").append(this.className).append("> getAll() throws ")
                .append(throwsExceptionString).append(";\n");
        body.append("}\n");

        try {
            Writer writer = processingEnv.getFiler()
                    .createSourceFile(this.packageName + "." + this.className + "Dao")
                    .openWriter();
            writer.write(body.toString());
            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void generateDao(List<VariableElement> fields, List<VariableElement> uniqueKeyFields) {
        StringBuilder throwsExceptionString = new StringBuilder();
        for (int i=0; i < EXCEPTION_CLASS_NAMES.size(); i++) {
            throwsExceptionString.append(this.className).append(EXCEPTION_CLASS_NAMES.get(i));
            if (i != EXCEPTION_CLASS_NAMES.size()-1) {
                throwsExceptionString.append(", ");
            }
        }

        StringBuilder body = new StringBuilder();
        body.append("package ").append(this.packageName).append(";\n\n");
        for (String exception : EXCEPTION_CLASS_NAMES) {
            body.append("import ").append(this.packageName).append(".")
                    .append(this.className).append(exception).append(";\n");
        }
        body.append("\nimport java.io.EOFException;\n");
        body.append("import java.io.File;\n");
        body.append("import java.io.FileInputStream;\n");
        body.append("import java.io.FileOutputStream;\n");
        body.append("import java.io.IOException;\n");
        body.append("import java.io.ObjectInputStream;\n");
        body.append("import java.io.ObjectOutputStream;\n\n");
        body.append("import java.util.ArrayList;\n");
        body.append("import java.util.List;\n");
        body.append("import java.util.UUID;\n\n");
        body.append("public class ").append(this.className).append("DaoImpl implements ")
                .append(this.className).append("Dao {\n");
        body.append("   private final String dbFile = \"student.db\";\n\n");
        body.append("   @Override\n");
        body.append("   public void add(").append(this.className).append(" ")
                .append(this.className.toLowerCase()).append(") throws ").append(throwsExceptionString).append(" {\n");
        body.append("       ").append(this.className).append(DTO_SUFFIX).append(" ")
                .append(this.className.toLowerCase()).append(DTO_SUFFIX).append(" = convert").append(this.className)
                .append("To").append(this.className).append(DTO_SUFFIX).append("(").append(this.className.toLowerCase())
                .append(");\n");
        body.append("       List<").append(this.className).append("> ")
                .append(this.className.toLowerCase()).append("s = getAll();\n");
        body.append("       ").append(this.className.toLowerCase()).append("s.add(")
                .append(this.className.toLowerCase()).append(");\n");
        body.append("       try (FileOutputStream fileOut = new FileOutputStream(dbFile);\n");
        body.append("            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {\n");
        body.append("           for (").append(this.className).append(" ")
                .append("random : ").append(this.className.toLowerCase()).append("s) {\n");
        body.append("               objectOut.writeObject(convert").append(this.className).append("To").append(this.className).append(DTO_SUFFIX).append("(random));\n");
        body.append("           }\n");
        body.append("       } catch(IOException e) {\n");
        body.append("           throw new ").append(this.className)
                .append("ServiceException(\"Internal service error, please try again!\" + e);\n");
        body.append("       }\n");
        body.append("   }\n\n");

        body.append("   @Override\n");
        body.append("   public List<").append(this.className).append("> getAll() throws ")
                .append(throwsExceptionString).append(" {\n");
        body.append("       List<").append(this.className).append("> ")
                .append(this.className.toLowerCase()).append("s = new ArrayList<>();\n");
        body.append("       if (!this.dbExist()) {\n");
        body.append("           return ").append(this.className.toLowerCase()).append("s;\n");
        body.append("       }\n");
        body.append("       try (FileInputStream fileIn = new FileInputStream(dbFile);\n");
        body.append("            ObjectInputStream objectIn = new ObjectInputStream(fileIn)) {\n");
        body.append("           while(true) {\n");
        body.append("               try {\n");
        body.append("                   ").append(this.className).append(DTO_SUFFIX).append(" ")
                .append(this.className.toLowerCase()).append(DTO_SUFFIX).append(" = (")
                .append(this.className).append(DTO_SUFFIX).append(") objectIn.readObject();\n");
        body.append("                   ").append(this.className.toLowerCase()).append("s.add(convert")
                .append(this.className).append(DTO_SUFFIX).append("To").append(this.className).append("(")
                .append(this.className.toLowerCase()).append(DTO_SUFFIX).append("));\n");
        body.append("               } catch (EOFException eof) {\n");
        body.append("                   break;\n");
        body.append("               }\n");
        body.append("           }\n");
        body.append("       } catch(IOException | ClassNotFoundException e) {\n");
        body.append("           throw new ").append(this.className).append("ServiceException(\"Internal service error occurred: \" + e);\n");
        body.append("       }\n");
        body.append("       return ").append(this.className.toLowerCase()).append("s;\n");
        body.append("   }\n\n");

        body.append("   @Override\n");
        body.append("   public void delete(").append(this.className).append(" ")
                .append(this.className.toLowerCase()).append(") throws ").append(throwsExceptionString).append(" {\n");
        body.append("       if (!this.dbExist()) {\n");
        body.append("           throw new ").append(this.className)
                .append("RequestException(\"Unable to find the student\");\n");
        body.append("       }\n");
        body.append("       boolean isDeleted = false;\n");
        body.append("       List<").append(this.className).append("> ")
                .append(this.className.toLowerCase()).append("s = this.getAll();\n");
        body.append("       try (FileOutputStream fileOut = new FileOutputStream(dbFile);\n");
        body.append("            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {\n");
        body.append("           for (").append(this.className).append(" random : ")
                .append(this.className.toLowerCase()).append("s) {\n");
        body.append("               if(!");
        for (int i=0; i < uniqueKeyFields.size(); i++) {
            String fieldName = uniqueKeyFields.get(i).getSimpleName().toString();
            String fieldGetterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            if (uniqueKeyFields.get(i).asType().getKind().isPrimitive()) {
                body.append("(").append("random.").append(fieldGetterName)
                        .append("() == ").append(this.className.toLowerCase()).append(".").append(fieldGetterName)
                        .append("())");
            } else {
                body.append("random.").append(fieldGetterName)
                        .append("().equals(").append(this.className.toLowerCase()).append(".").append(fieldGetterName)
                        .append("())");
            }

            if (i != uniqueKeyFields.size() - 1) {
                body.append(" && !");
            }
        }
        body.append(") {\n");
        body.append("                   objectOut.writeObject(convert").append(this.className).append("To")
                .append(this.className).append(DTO_SUFFIX).append("(random));\n");
        body.append("               } else {\n");
        body.append("                   isDeleted = true;\n");
        body.append("               }\n");
        body.append("           }\n");
        body.append("       } catch (IOException e) {\n");
        body.append("           throw new ").append(this.className).append("ServiceException(\"Internal service error occurred: \" + e);\n");
        body.append("       }\n\n");
        body.append("       if (!isDeleted) {\n");
        body.append("           throw new ").append(this.className).append("RequestException(\"Data not found: \");\n");
        body.append("       }\n");
        body.append("   }\n\n");

        body.append("   @Override\n");
        body.append("   public void update(").append(this.className).append(" ")
                .append(this.className.toLowerCase()).append(") throws ").append(throwsExceptionString).append(" {\n");
        body.append("       if (!this.dbExist()) {\n");
        body.append("           throw new ").append(this.className)
                .append("RequestException(\"Unable to find the student\");\n");
        body.append("       }\n");
        body.append("       boolean isUpdated = false;\n");
        body.append("       List<").append(this.className).append("> ")
                .append(this.className.toLowerCase()).append("s = this.getAll();\n");
        body.append("       try (FileOutputStream fileOut = new FileOutputStream(dbFile);\n");
        body.append("            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {\n");
        body.append("           for (").append(this.className).append(" random : ")
                .append(this.className.toLowerCase()).append("s) {\n");
        body.append("               if(");
        for (int i=0; i < uniqueKeyFields.size(); i++) {
            String fieldName = uniqueKeyFields.get(i).getSimpleName().toString();
            String fieldGetterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            if (uniqueKeyFields.get(i).asType().getKind().isPrimitive()) {
                body.append("random.").append(fieldGetterName).append("() == ").append(this.className.toLowerCase())
                        .append(".").append(fieldGetterName).append("()");
            } else {
                body.append("random.").append(fieldGetterName).append("().equals(").append(this.className.toLowerCase())
                        .append(".").append(fieldGetterName).append("())");
            }

            if (i != uniqueKeyFields.size() - 1) {
                body.append(" && ");
            }
        }
        body.append(") {\n");
        body.append("                   objectOut.writeObject(convert").append(this.className).append("To")
                .append(this.className).append(DTO_SUFFIX).append("(").append(this.className.toLowerCase())
                .append("));\n");
        body.append("                   isUpdated=true;\n");
        body.append("               } else {\n");
        body.append("                   objectOut.writeObject(convert").append(this.className).append("To")
                .append(this.className).append(DTO_SUFFIX).append("(random));\n");
        body.append("               }\n");
        body.append("           }\n");
        body.append("       } catch (IOException e) {\n");
        body.append("           throw new ").append(this.className).append("ServiceException(\"Internal service error occurred: \" + e);\n");
        body.append("       }\n\n");
        body.append("       if (!isUpdated) {\n");
        body.append("           throw new ").append(this.className).append("RequestException(\"Data not found: \");\n");
        body.append("       }\n");
        body.append("   }\n\n");
        body.append("   private boolean dbExist() {\n");
        body.append("       File file = new File(dbFile);\n");
        body.append("       return file.exists();\n");
        body.append("   }\n\n");

        body.append("   private ").append(this.className).append(" ").append("convert").append(this.className)
                .append(DTO_SUFFIX).append("To").append(this.className).append("(").append(this.className)
                .append(DTO_SUFFIX).append(" ").append(this.className.toLowerCase()).append(DTO_SUFFIX).append(") {\n");
        body.append("       ").append(this.className).append(" ").append(this.className.toLowerCase())
                .append(" = new ").append(this.className).append("();\n");
        for (VariableElement field : fields) {
            String fieldName = field.getSimpleName().toString();
            String fieldGetterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String fieldSetterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            body.append("       ").append(this.className.toLowerCase()).append(".").append(fieldSetterName)
                    .append("(").append(this.className.toLowerCase()).append(DTO_SUFFIX).append(".")
                    .append(fieldGetterName).append("());\n");
        }

        for (VariableElement field : uniqueKeyFields) {
            String fieldName = field.getSimpleName().toString();
            String fieldGetterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String fieldSetterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            body.append("       ").append(this.className.toLowerCase()).append(".").append(fieldSetterName)
                    .append("(").append(this.className.toLowerCase()).append(DTO_SUFFIX).append(".")
                    .append(fieldGetterName).append("());\n");
        }
        body.append("       return ").append(this.className.toLowerCase()).append(";\n");
        body.append("   }\n\n");

        body.append("   private ").append(this.className).append(DTO_SUFFIX).append(" ").append("convert").append(this.className)
                .append("To").append(this.className).append(DTO_SUFFIX).append("(").append(this.className)
                .append(" ").append(this.className.toLowerCase()).append(") {\n");
        body.append("       ").append(this.className).append(DTO_SUFFIX).append(" ").append(this.className.toLowerCase())
                .append(DTO_SUFFIX).append(" = new ").append(this.className).append(DTO_SUFFIX).append("();\n");
        for (VariableElement field : fields) {
            String fieldName = field.getSimpleName().toString();
            String fieldGetterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String fieldSetterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            body.append("       ").append(this.className.toLowerCase()).append(DTO_SUFFIX).append(".").append(fieldSetterName)
                    .append("(").append(this.className.toLowerCase()).append(".").append(fieldGetterName).append("());\n");
        }

        for (VariableElement field : uniqueKeyFields) {
            String fieldName = field.getSimpleName().toString();
            String fieldGetterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String fieldSetterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            body.append("       ").append(this.className.toLowerCase()).append(DTO_SUFFIX).append(".").append(fieldSetterName)
                    .append("(").append(this.className.toLowerCase()).append(".").append(fieldGetterName).append("());\n");
        }
        body.append("       return ").append(this.className.toLowerCase()).append(DTO_SUFFIX).append(";\n");
        body.append("   }\n\n");

        body.append("}\n");

        try {
            Writer writer = processingEnv.getFiler()
                    .createSourceFile(this.packageName + "." + this.className + "DaoImpl")
                    .openWriter();
            writer.write(body.toString());
            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
