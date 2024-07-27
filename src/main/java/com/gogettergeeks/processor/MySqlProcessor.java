package com.gogettergeeks.processor;

import com.gogettergeeks.annotation.MySqlGenerated;
import com.gogettergeeks.annotation.Persisted;
import com.gogettergeeks.annotation.UniqueKey;
import com.gogettergeeks.utils.StringUtil;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("com.gogettergeeks.annotation.MySqlGenerated")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class MySqlProcessor extends AbstractProcessor {
    private static final String DTO_SUFFIX = "GeneratedDto";

    private String packageName;
    private String className;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean isClaimed = false;
        Set<? extends Element> fileDbGeneratedAnnotatedClasses = roundEnv.getElementsAnnotatedWith(MySqlGenerated.class);
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
                    generateDto(uniqueKeyFields, fields);
                    generateDao(uniqueKeyFields, fields);
                }
            }
        }

        return isClaimed;
    }

    private void generateDto(List<VariableElement> uniqueKeyFields, List<VariableElement> fields) {
        List<MethodSpec> methodSpecs = new ArrayList<>();
        List<FieldSpec> fieldsSpec = new ArrayList<>();
        for (VariableElement uniqueKey : uniqueKeyFields) {
            String fieldName = uniqueKey.getSimpleName().toString();

            FieldSpec fieldSpec = FieldSpec.builder(TypeName.get(uniqueKey.asType()), fieldName)
                    .addModifiers(Modifier.PRIVATE)
                    .build();

            MethodSpec setterMethodSpec = MethodSpec.methodBuilder("set" + StringUtil.capitalizeFirstLetter(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(TypeName.get(uniqueKey.asType()), fieldName)
                    .addStatement("this." + fieldName + " = " + fieldName)
                    .build();

            MethodSpec getterMethodSpec = MethodSpec.methodBuilder("get" + StringUtil.capitalizeFirstLetter(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.get(uniqueKey.asType()))
                    .addStatement("return this." + fieldName)
                    .build();

            fieldsSpec.add(fieldSpec);
            methodSpecs.add(setterMethodSpec);
            methodSpecs.add(getterMethodSpec);
        }

        for (VariableElement field : fields) {
            String fieldName = field.getSimpleName().toString();

            FieldSpec fieldSpec = FieldSpec.builder(TypeName.get(field.asType()), fieldName)
                    .addModifiers(Modifier.PRIVATE)
                    .build();

            MethodSpec setterMethodSpec = MethodSpec.methodBuilder("set" + StringUtil.capitalizeFirstLetter(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(TypeName.get(field.asType()), fieldName)
                    .addStatement("this." + fieldName + " = " + fieldName)
                    .build();

            MethodSpec getterMethodSpec = MethodSpec.methodBuilder("get" + StringUtil.capitalizeFirstLetter(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.get(field.asType()))
                    .addStatement("return this." + fieldName)
                    .build();

            fieldsSpec.add(fieldSpec);
            methodSpecs.add(setterMethodSpec);
            methodSpecs.add(getterMethodSpec);
        }

        TypeSpec generatedDtoSpec = TypeSpec.classBuilder(className + DTO_SUFFIX)
                .addModifiers(Modifier.PUBLIC)
                .addFields(fieldsSpec)
                .addMethods(methodSpecs)
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, generatedDtoSpec).build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateDao(List<VariableElement> uniqueFields, List<VariableElement> fields) {
        FieldSpec connectionUrlFieldSpec = FieldSpec
                .builder(String.class, "connectionUrl", Modifier.PRIVATE, Modifier.FINAL)
                .build();
        FieldSpec usernameFieldSpec = FieldSpec
                .builder(String.class, "username", Modifier.PRIVATE, Modifier.FINAL)
                .build();
        FieldSpec passwordFieldSpec = FieldSpec
                .builder(String.class, "password", Modifier.PRIVATE, Modifier.FINAL)
                .build();

        ParameterSpec connectionUrlParamSpec = ParameterSpec.builder(String.class, "connectionUrl")
                .build();
        ParameterSpec usernameParamSpec = ParameterSpec.builder(String.class, "username")
                .build();
        ParameterSpec passwordParamSpec = ParameterSpec.builder(String.class, "password")
                .build();
        MethodSpec constructorSpec = MethodSpec.constructorBuilder()
                .addParameters(Arrays.asList(connectionUrlParamSpec, usernameParamSpec, passwordParamSpec))
                .addStatement("this.connectionUrl = connectionUrl;")
                .addStatement("this.username = username;")
                .addStatement("this.password = password;")
                .build();

        List<MethodSpec> methodSpecs = Arrays.asList(
                getCreateMethodSpec(uniqueFields, fields),
                getReadMethodSpec(uniqueFields, fields),
                getUpdateMethodSpec(uniqueFields, fields),
                getDeleteMethodSpec(uniqueFields)
        );

        TypeSpec daoSpec = TypeSpec.classBuilder(className + "Dao")
                .addModifiers(Modifier.PUBLIC)
                .addFields(Arrays.asList(connectionUrlFieldSpec, usernameFieldSpec, passwordFieldSpec))
                .addMethod(constructorSpec)
                .addMethods(methodSpecs)
                .addMethod(convertDtoToGeneratedDtoMethodSpec(uniqueFields, fields))
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, daoSpec).build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MethodSpec getCreateMethodSpec(List<VariableElement> uniqueFields, List<VariableElement> fields) {
        CodeBlock.Builder methodBodyBuilder = CodeBlock.builder();
        methodBodyBuilder.add(className + DTO_SUFFIX + " generatedDto = convert(" + className.toLowerCase() + ");");
        methodBodyBuilder.add("String query = \"INSERT INTO " + className.toLowerCase() + "(");

        StringBuilder questionMarks = new StringBuilder();
        StringBuilder preparedStatements = new StringBuilder();
        int queryParamIndex = 1;
        for (int i=0; i < uniqueFields.size(); i++, queryParamIndex++) {
            VariableElement uniqueField = uniqueFields.get(i);
            String columnName = StringUtil.camelCaseToUnderscore(uniqueField.getSimpleName().toString());
            methodBodyBuilder.add(columnName);
            questionMarks.append("?");

            String variableSimpleTypeName = uniqueField.asType().getKind().isPrimitive() ?
                    uniqueField.asType().toString() : getSimpleClassName(uniqueField);
            preparedStatements.append("preparedStatement.set")
                    .append(StringUtil.capitalizeFirstLetter(variableSimpleTypeName))
                    .append("(").append(queryParamIndex).append(", generatedDto.get")
                    .append(StringUtil.capitalizeFirstLetter(uniqueField.getSimpleName().toString())).append("());");

            if (fields.size() > 0) {
                methodBodyBuilder.add(", ");
                questionMarks.append(", ");
            } else {
                if (i != uniqueFields.size()-1) {
                    methodBodyBuilder.add(", ");
                    questionMarks.append(", ");
                }
            }
        }

        for (int i=0; i < fields.size(); i++, queryParamIndex++) {
            VariableElement field = fields.get(i);
            String columnName = StringUtil.camelCaseToUnderscore(field.getSimpleName().toString());
            methodBodyBuilder.add(columnName);
            questionMarks.append("?");

            String variableSimpleTypeName = field.asType().getKind().isPrimitive() ?
                    field.asType().toString() : getSimpleClassName(field);
            preparedStatements.append("preparedStatement.set")
                    .append(StringUtil.capitalizeFirstLetter(variableSimpleTypeName))
                    .append("(").append(queryParamIndex).append(", generatedDto.get")
                    .append(StringUtil.capitalizeFirstLetter(field.getSimpleName().toString())).append("());");

            if (i != fields.size()-1) {
                methodBodyBuilder.add(", ");
                questionMarks.append(", ");
            }
        }
        preparedStatements.append("preparedStatement.executeUpdate();");

        methodBodyBuilder.add(") VALUES (" + questionMarks + ")\";");
        methodBodyBuilder.add("try ($T connection = $T.getConnection(this.connectionUrl, username, password);",
                Connection.class, DriverManager.class);
        methodBodyBuilder.add("$T preparedStatement = connection.prepareStatement(query)) {", PreparedStatement.class);
        methodBodyBuilder.add(preparedStatements.toString());
        methodBodyBuilder.add("}");

        return MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(packageName, className), className.toLowerCase())
                .addException(SQLException.class)
                .addStatement(methodBodyBuilder.build())
                .build();
    }

    private MethodSpec getReadMethodSpec(List<VariableElement> uniqueFields, List<VariableElement> fields) {
        ClassName list = ClassName.get("java.util", "List");
        ClassName arrayList = ClassName.get("java.util", "ArrayList");
        ClassName userProvidedDto = ClassName.get(packageName, className);

        CodeBlock.Builder methodBodyBuilder = CodeBlock.builder();
        methodBodyBuilder.add("$T dtoList = new $T();", ParameterizedTypeName.get(list, userProvidedDto),
                ParameterizedTypeName.get(arrayList, userProvidedDto));
        methodBodyBuilder.add("String query = \"SELECT * FROM $L\";", className.toLowerCase());
        methodBodyBuilder.add("try ($T connection = $T.getConnection(this.connectionUrl, username, password);",
                Connection.class, DriverManager.class);
        methodBodyBuilder.add("$T statement = connection.createStatement();", Statement.class);
        methodBodyBuilder.add("$T resultSet = statement.executeQuery(query)) {", ResultSet.class);
        methodBodyBuilder.add("while (resultSet.next()) {");
        methodBodyBuilder.add("$T dto = new $T();", userProvidedDto, userProvidedDto);

        for (VariableElement uniqueField : uniqueFields) {
            String columnName = StringUtil.camelCaseToUnderscore(uniqueField.getSimpleName().toString());
            String variableSimpleTypeName = uniqueField.asType().getKind().isPrimitive() ?
                    uniqueField.asType().toString() : getSimpleClassName(uniqueField);
            methodBodyBuilder.add("dto.set$L(resultSet.get$L($S));",
                    StringUtil.capitalizeFirstLetter(uniqueField.getSimpleName().toString()),
                    StringUtil.capitalizeFirstLetter(variableSimpleTypeName), columnName);
        }

        for (VariableElement field : fields) {
            String columnName = StringUtil.camelCaseToUnderscore(field.getSimpleName().toString());
            String variableSimpleTypeName = field.asType().getKind().isPrimitive() ?
                    field.asType().toString() : getSimpleClassName(field);
            methodBodyBuilder.add("dto.set$L(resultSet.get$L($S));",
                    StringUtil.capitalizeFirstLetter(field.getSimpleName().toString()),
                    StringUtil.capitalizeFirstLetter(variableSimpleTypeName), columnName);
        }

        methodBodyBuilder.add("dtoList.add(dto);");
        methodBodyBuilder.add("}");
        methodBodyBuilder.add("}");
        methodBodyBuilder.add("return dtoList");

        TypeName listOfUserProvidedDto = ParameterizedTypeName.get(list, userProvidedDto);
        return MethodSpec.methodBuilder("read")
                .addModifiers(Modifier.PUBLIC)
                .returns(listOfUserProvidedDto)
                .addException(SQLException.class)
                .addStatement(methodBodyBuilder.build())
                .build();
    }

    private MethodSpec getDeleteMethodSpec(List<VariableElement> uniqueFields) {
        CodeBlock.Builder methodBodyBuilder = CodeBlock.builder();
        methodBodyBuilder.add(className + DTO_SUFFIX + " generatedDto = convert(" + className.toLowerCase() + ");");
        methodBodyBuilder.add("String query = \"DELETE FROM " + className.toLowerCase() + " WHERE ");

        StringBuilder preparedStatements = new StringBuilder();
        int queryParamIndex = 1;
        for (int i=0; i < uniqueFields.size(); i++, queryParamIndex++) {

            VariableElement uniqueField = uniqueFields.get(i);
            String columnName = StringUtil.camelCaseToUnderscore(uniqueField.getSimpleName().toString());
            methodBodyBuilder.add(columnName).add(" = ?");

            String variableSimpleTypeName = uniqueField.asType().getKind().isPrimitive() ?
                    uniqueField.asType().toString() : getSimpleClassName(uniqueField);
            preparedStatements.append("preparedStatement.set")
                    .append(StringUtil.capitalizeFirstLetter(variableSimpleTypeName))
                    .append("(").append(queryParamIndex).append(", generatedDto.get")
                    .append(StringUtil.capitalizeFirstLetter(uniqueField.getSimpleName().toString())).append("());");

            if (i != uniqueFields.size()-1) {
                methodBodyBuilder.add(" AND ");
            }
        }


        preparedStatements.append("preparedStatement.executeUpdate();");

        methodBodyBuilder.add("\";");
        methodBodyBuilder.add("try ($T connection = $T.getConnection(this.connectionUrl, username, password);", Connection.class, DriverManager.class);
        methodBodyBuilder.add("$T preparedStatement = connection.prepareStatement(query)) {", PreparedStatement.class);
        methodBodyBuilder.add(preparedStatements.toString());
        methodBodyBuilder.add("}");

        return MethodSpec.methodBuilder("delete")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(packageName, className), className.toLowerCase())
                .addException(SQLException.class)
                .addStatement(methodBodyBuilder.build())
                .build();
    }

    private MethodSpec getUpdateMethodSpec(List<VariableElement> uniqueFields, List<VariableElement> fields) {
        CodeBlock.Builder methodBodyBuilder = CodeBlock.builder();
        methodBodyBuilder.add(className + DTO_SUFFIX + " generatedDto = convert(" + className.toLowerCase() + ");");
        methodBodyBuilder.add("String query = \"UPDATE " + className.toLowerCase() + " SET ");

        StringBuilder preparedStatements = new StringBuilder();
        int queryParamIndex = 1;
        for (int i=0; i < fields.size(); i++, queryParamIndex++) {
            VariableElement field = fields.get(i);
            String columnName = StringUtil.camelCaseToUnderscore(field.getSimpleName().toString());
            methodBodyBuilder.add(columnName).add(" = ?");

            String variableSimpleTypeName = field.asType().getKind().isPrimitive() ?
                    field.asType().toString() : getSimpleClassName(field);
            preparedStatements.append("preparedStatement.set")
                    .append(StringUtil.capitalizeFirstLetter(variableSimpleTypeName))
                    .append("(").append(queryParamIndex).append(", generatedDto.get")
                    .append(StringUtil.capitalizeFirstLetter(field.getSimpleName().toString())).append("());");

            if (i != fields.size()-1) {
                methodBodyBuilder.add(", ");
            }
        }

        methodBodyBuilder.add(" WHERE ");
        for (int i=0; i < uniqueFields.size(); i++, queryParamIndex++) {

            VariableElement uniqueField = uniqueFields.get(i);
            String columnName = StringUtil.camelCaseToUnderscore(uniqueField.getSimpleName().toString());
            methodBodyBuilder.add(columnName).add(" = ?");

            String variableSimpleTypeName = uniqueField.asType().getKind().isPrimitive() ?
                    uniqueField.asType().toString() : getSimpleClassName(uniqueField);
            preparedStatements.append("preparedStatement.set")
                    .append(StringUtil.capitalizeFirstLetter(variableSimpleTypeName))
                    .append("(").append(queryParamIndex).append(", generatedDto.get")
                    .append(StringUtil.capitalizeFirstLetter(uniqueField.getSimpleName().toString())).append("());");

            if (i != uniqueFields.size()-1) {
                methodBodyBuilder.add(" AND ");
            }
        }


        preparedStatements.append("preparedStatement.executeUpdate();");

        methodBodyBuilder.add("\";");
        methodBodyBuilder.add("try ($T connection = $T.getConnection(this.connectionUrl, username, password);", Connection.class, DriverManager.class);
        methodBodyBuilder.add("$T preparedStatement = connection.prepareStatement(query)) {", PreparedStatement.class);
        methodBodyBuilder.add(preparedStatements.toString());
        methodBodyBuilder.add("}");

        return MethodSpec.methodBuilder("update")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(packageName, className), className.toLowerCase())
                .addException(SQLException.class)
                .addStatement(methodBodyBuilder.build())
                .build();
    }

    private MethodSpec convertDtoToGeneratedDtoMethodSpec(List<VariableElement> uniqueFields, List<VariableElement> fields) {
        CodeBlock.Builder convertMethodBuilder = CodeBlock.builder();
        convertMethodBuilder.add(className + DTO_SUFFIX + " ");
        convertMethodBuilder.add("generatedDto = new " + className + DTO_SUFFIX + "();");
        for (VariableElement uniqueField : uniqueFields) {
            String capitalizedFieldName = StringUtil.capitalizeFirstLetter(uniqueField.getSimpleName().toString());
            convertMethodBuilder.add("generatedDto.set" + capitalizedFieldName + "(");
            convertMethodBuilder.add(className.toLowerCase() + ".get" + capitalizedFieldName + "());");
        }

        for (VariableElement field : fields) {
            String capitalizedFieldName = StringUtil.capitalizeFirstLetter(field.getSimpleName().toString());
            convertMethodBuilder.add("generatedDto.set" + capitalizedFieldName + "(");
            convertMethodBuilder.add(className.toLowerCase() + ".get" + capitalizedFieldName + "());");
        }
        convertMethodBuilder.add("return generatedDto");

        return MethodSpec.methodBuilder("convert")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(ClassName.get(packageName, className), className.toLowerCase())
                .returns(ClassName.get(packageName, className + DTO_SUFFIX))
                .addStatement(convertMethodBuilder.build())
                .build();
    }

    private String getSimpleClassName(VariableElement variableElement) {
        TypeMirror typeMirror = variableElement.asType();
        Element typeElement = processingEnv.getTypeUtils().asElement(typeMirror);
        return typeElement.getSimpleName().toString();
    }
}
