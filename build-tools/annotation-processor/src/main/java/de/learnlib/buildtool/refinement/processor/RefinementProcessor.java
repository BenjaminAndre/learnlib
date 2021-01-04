/* Copyright (C) 2013-2021 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.buildtool.refinement.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic.Kind;

import com.github.misberner.apcommons.util.ElementUtils;
import com.github.misberner.apcommons.util.annotations.AnnotationUtils;
import com.github.misberner.apcommons.util.methods.MethodUtils;
import com.github.misberner.apcommons.util.methods.ParameterInfo;
import com.github.misberner.apcommons.util.types.TypeUtils;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import de.learnlib.buildtool.refinement.annotation.GenerateRefinement;
import de.learnlib.buildtool.refinement.annotation.GenerateRefinements;
import de.learnlib.buildtool.refinement.annotation.Generic;
import de.learnlib.buildtool.refinement.annotation.Interface;
import de.learnlib.buildtool.refinement.annotation.Map;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SuppressWarnings("nullness")
public class RefinementProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(GenerateRefinements.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GenerateRefinements.class);

        for (Element elem : elements) {

            validateAnnotation(elem);

            final GenerateRefinements generateRefinements = elem.getAnnotation(GenerateRefinements.class);

            int idx = 0;

            for (final GenerateRefinement annotation : generateRefinements.value()) {

                final TypeElement annotatedClass = (TypeElement) elem;

                final TypeSpec.Builder builder = createClass(annotatedClass, annotation);
                addGenerics(builder, annotation);
                addSuperClass(builder, annotatedClass, annotation);
                addInterfaces(builder, annotation);
                addConstructors(builder, annotatedClass, annotation, idx);

                try {
                    JavaFile.builder(ElementUtils.getPackageName(elem), builder.build())
                            .addFileComment("This class has been generated by $L.\n" +
                                            "Do not edit this class, changes will be overridden.", getClass().getName())
                            .build()
                            .writeTo(super.processingEnv.getFiler());
                } catch (IOException e) {
                    error("Could not writer source: " + e.getMessage());
                }

                idx++;
            }
        }
        return true;
    }

    private void validateAnnotation(final Element element) {
        if (element.getKind() != ElementKind.CLASS) {
            error("Annotation " + GenerateRefinement.class + " is only supported on class level");
            throw new IllegalArgumentException();
        }
    }

    private TypeSpec.Builder createClass(TypeElement annotatedClass, GenerateRefinement annotation) {
        return TypeSpec.classBuilder(annotation.name())
                       .addModifiers(Modifier.PUBLIC)
                       .addJavadoc("This is an auto-generated refinement. See the {@link $T original class}.\n",
                                   processingEnv.getTypeUtils().erasure(annotatedClass.asType()));
    }

    private void addGenerics(TypeSpec.Builder builder, GenerateRefinement annotation) {
        for (String typeParameter : annotation.generics()) {
            builder.addTypeVariable(TypeVariableName.get(typeParameter));
        }
    }

    private void addSuperClass(TypeSpec.Builder builder, TypeElement annotatedClass, GenerateRefinement annotation) {

        final List<TypeName> generics = new ArrayList<>(annotation.parentGenerics().length);
        for (Generic generic : annotation.parentGenerics()) {
            generics.add(extractGeneric(generic));
        }

        builder.superclass(ParameterizedTypeName.get(ClassName.get(annotatedClass), generics.toArray(new TypeName[0])));
    }

    private void addInterfaces(TypeSpec.Builder builder, GenerateRefinement annotation) {

        for (Interface inter : annotation.interfaces()) {

            final ClassName className = extractClass(inter, Interface::clazz);

            final List<TypeName> generics = new ArrayList<>(annotation.interfaces().length);
            for (String generic : inter.generics()) {
                generics.add(TypeVariableName.get(generic));
            }

            builder.addSuperinterface(ParameterizedTypeName.get(className, generics.toArray(new TypeName[0])));
        }
    }

    private void addConstructors(TypeSpec.Builder builder,
                                 TypeElement annotatedClass,
                                 GenerateRefinement annotation,
                                 int idx) {

        final AnnotationMirror generateRefinementsMirror =
                AnnotationUtils.findAnnotationMirror(annotatedClass, GenerateRefinements.class);

        final List<? extends AnnotationValue> values = find(generateRefinementsMirror, "value");
        final AnnotationMirror generateRefinementMirror = (AnnotationMirror) values.get(idx).getValue();

        final List<? extends AnnotationValue> parameterMapping = find(generateRefinementMirror, "parameterMapping");

        for (final ExecutableElement constructor : TypeUtils.getConstructors(annotatedClass)) {

            final MethodSpec.Builder mBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

            final StringJoiner javadocJoiner = new StringJoiner(", ",
                                                                "This is an auto-generated constructor. See the {@link $T#$T(",
                                                                ") original constructor}.\n");

            final int numOfConstructorParams = constructor.getParameters().size();
            final List<String> parameterNames = new ArrayList<>(numOfConstructorParams);
            final List<TypeMirror> javadocTypes = new ArrayList<>(numOfConstructorParams + 2);

            // references to <class>#<constructor>
            javadocTypes.add(processingEnv.getTypeUtils().erasure(annotatedClass.asType()));
            javadocTypes.add(processingEnv.getTypeUtils().erasure(annotatedClass.asType()));

            for (final ParameterInfo info : MethodUtils.getParameterInfos(constructor)) {
                javadocJoiner.add("$T");
                javadocTypes.add(processingEnv.getTypeUtils().erasure(info.getType()));
                parameterNames.add(info.getName());

                if (info.isVarArgs()) {
                    mBuilder.varargs(true);
                    mBuilder.addAnnotation(SafeVarargs.class);
                }

                final TypeName typeName = buildTypeName(annotation, info.getType(), parameterMapping, info.isVarArgs());

                mBuilder.addParameter(typeName, info.getName());
            }

            final StringJoiner sj = new StringJoiner(", ", "super(", ")");
            parameterNames.forEach(sj::add);

            mBuilder.addStatement(CodeBlock.of(sj.toString(), parameterNames.toArray()));
            mBuilder.addJavadoc(javadocJoiner.toString(), javadocTypes.toArray());
            builder.addMethod(mBuilder.build());
        }
    }

    private TypeName buildTypeName(GenerateRefinement annotation,
                                   TypeMirror typeMirror,
                                   List<? extends AnnotationValue> parameterMapping,
                                   boolean isVarArgs) {

        final Map[] parametersAnn = annotation.parameterMapping();
        final boolean isWildcard = typeMirror.getKind() == TypeKind.WILDCARD;
        final TypeMirror typeToCompare;

        if (isVarArgs && typeMirror.getKind() == TypeKind.ARRAY) {
            typeToCompare = ((ArrayType) typeMirror).getComponentType();
        } else {
            typeToCompare = typeMirror;
        }

        ClassName replacementClass = null;
        int i = 0;
        for (AnnotationValue parameter : parameterMapping) {
            AnnotationMirror parameterMirror = (AnnotationMirror) parameter.getValue();
            TypeMirror fromAttribute = find(parameterMirror, "from");
            if (processingEnv.getTypeUtils()
                             .isSameType(processingEnv.getTypeUtils().erasure(typeToCompare), fromAttribute)) {
                final TypeMirror toAttribute = find(parameterMirror, "to");
                replacementClass =
                        ClassName.get(this.processingEnv.getElementUtils().getTypeElement(toAttribute.toString()));
                break;
            }
            i++;
        }

        if (replacementClass != null) {
            final Map map = parametersAnn[i];
            final List<TypeName> generics =
                    new ArrayList<>(Math.max(map.withGenerics().length, map.withComplexGenerics().length));

            if (map.withGenerics().length > 0) {
                for (String generic : map.withGenerics()) {
                    generics.add(TypeVariableName.get(generic));
                }
            } else if (map.withComplexGenerics().length > 0) {
                for (Generic generic : map.withComplexGenerics()) {
                    generics.add(extractGeneric(generic));
                }
            }

            final ParameterizedTypeName parameterizedTypeName =
                    ParameterizedTypeName.get(replacementClass, generics.toArray(new TypeName[0]));
            final TypeName typeName;

            if (isWildcard) {
                if (((WildcardType) typeMirror).getExtendsBound() != null) {
                    typeName = WildcardTypeName.subtypeOf(parameterizedTypeName);
                } else {
                    typeName = WildcardTypeName.supertypeOf(parameterizedTypeName);
                }
            } else {
                typeName = parameterizedTypeName;
            }

            if (isVarArgs) {
                return ArrayTypeName.of(typeName);
            }

            return typeName;
        } else { // no replacement

            if (typeMirror.getKind() == TypeKind.DECLARED) {
                final DeclaredType declaredType = (DeclaredType) typeMirror;

                if (declaredType.getTypeArguments().isEmpty()) {
                    return TypeName.get(typeMirror);
                }

                final List<TypeName> genericTypeNames = new ArrayList<>(declaredType.getTypeArguments().size());

                for (TypeMirror t : declaredType.getTypeArguments()) {
                    genericTypeNames.add(buildTypeName(annotation, t, parameterMapping, false));
                }

                return ParameterizedTypeName.get(ClassName.get(this.processingEnv.getElementUtils()
                                                                                 .getTypeElement(declaredType.asElement()
                                                                                                             .toString())),
                                                 genericTypeNames.toArray(new TypeName[0]));
            }

            return TypeName.get(typeMirror);
        }
    }

    private TypeName extractGeneric(final Generic annotation) {

        final ClassName className = extractClass(annotation, Generic::clazz);

        if (!ClassName.get(Void.class).equals(className)) { // no default value

            if (annotation.generics().length > 0) {
                final List<TypeName> genericModels = new ArrayList<>(annotation.generics().length);

                for (final String generic : annotation.generics()) {
                    genericModels.add(TypeVariableName.get(generic));
                }

                return ParameterizedTypeName.get(className, genericModels.toArray(new TypeName[0]));
            }

            return className;
        } else if (!annotation.value().isEmpty()) {
            return TypeVariableName.get(annotation.value());
        } else {
            throw new IllegalArgumentException();
        }
    }

    private <T> ClassName extractClass(final T obj, Function<T, Class<?>> extractor) {
        try {
            final Class<?> clazz = extractor.apply(obj);
            return ClassName.get(clazz);
        } catch (MirroredTypeException mte) {
            DeclaredType classTypeMirror = (DeclaredType) mte.getTypeMirror();
            TypeElement classTypeElement = (TypeElement) classTypeMirror.asElement();
            return ClassName.get(classTypeElement);
        }
    }

    private void error(String msg) {
        processingEnv.getMessager().printMessage(Kind.ERROR, msg);
    }

    @SuppressWarnings("unchecked")
    private <T> T find(AnnotationMirror mirror, String name) {
        return (T) AnnotationUtils.findAnnotationValue(mirror, name).getValue();
    }

}
