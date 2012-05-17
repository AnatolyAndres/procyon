package com.strobel.reflection;

import com.strobel.core.VerifyArgument;

import java.lang.annotation.Annotation;

/**
 * @author strobelm
 */
final class GenericType<T> extends Type<T> {
    final static TypeBinder GenericBinder = new TypeBinder();

    private final Type _genericTypeDefinition;
    private final TypeBindings _typeBindings;

    private TypeList _interfaces;
    private Type _baseType;

    private FieldList _fields;
    private ConstructorList _constructors;
    private MethodList _methods;
    private TypeList _nestedTypes;

    GenericType(final Type genericTypeDefinition, final TypeBindings typeBindings) {
        _genericTypeDefinition = VerifyArgument.notNull(genericTypeDefinition, "genericTypeDefinition");
        _typeBindings = VerifyArgument.notNull(typeBindings, "typeBindings");
    }

    GenericType(final Type genericTypeDefinition, final TypeList typeArguments) {
        _genericTypeDefinition = VerifyArgument.notNull(genericTypeDefinition, "genericTypeDefinition");

        _typeBindings = TypeBindings.create(
            genericTypeDefinition.getTypeBindings().getGenericParameters(),
            VerifyArgument.notNull(typeArguments, "typeArguments")
        );
    }

    GenericType(final Type genericTypeDefinition, final Type... typeArguments) {
        _genericTypeDefinition = VerifyArgument.notNull(genericTypeDefinition, "genericTypeDefinition");

        _typeBindings = TypeBindings.create(
            genericTypeDefinition.getTypeBindings().getGenericParameters(),
            VerifyArgument.notNull(typeArguments, "typeArguments")
        );
    }

    private void ensureBaseType() {
        if (_baseType == null) {
            synchronized (CACHE_LOCK) {
                if (_baseType == null) {
                    final Type genericBaseType = _genericTypeDefinition.getBaseType();
                    if (genericBaseType == null || genericBaseType == NullType) {
                        _baseType = NullType;
                    }
                    else {
                        _baseType = GenericBinder.visit(genericBaseType, _typeBindings);
                    }
                }
            }
        }
    }

    private void ensureInterfaces() {
        if (_interfaces == null) {
            synchronized (CACHE_LOCK) {
                if (_interfaces == null) {
                    _interfaces = GenericBinder.visit(_genericTypeDefinition.getExplicitInterfaces(), _typeBindings);
                }
            }
        }
    }

    private void ensureFields() {
        if (_fields == null) {
            synchronized (CACHE_LOCK) {
                if (_fields == null) {
                    _fields = GenericBinder.visit(this, _genericTypeDefinition.getDeclaredFields(), _typeBindings);
                }
            }
        }
    }

    private void ensureConstructors() {
        if (_constructors == null) {
            synchronized (CACHE_LOCK) {
                if (_constructors == null) {
                    _constructors = GenericBinder.visit(this, _genericTypeDefinition.getDeclaredConstructors(), _typeBindings);
                }
            }
        }
    }

    private void ensureMethods() {
        if (_methods == null) {
            synchronized (CACHE_LOCK) {
                if (_methods == null) {
                    _methods = GenericBinder.visit(this, _genericTypeDefinition.getDeclaredMethods(), _typeBindings);
                }
            }
        }
    }

    private void ensureNestedTypes() {
        if (_nestedTypes == null) {
            synchronized (CACHE_LOCK) {
                if (_nestedTypes == null) {
                    _nestedTypes = GenericBinder.visit(_genericTypeDefinition.getDeclaredTypes(), _typeBindings);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<T> getErasedClass() {
        return (Class<T>)_genericTypeDefinition.getErasedClass();
    }

    @Override
    public TypeList getExplicitInterfaces() {
        ensureInterfaces();
        return _interfaces;
    }

    @Override
    public Type getBaseType() {
        ensureBaseType();
        final Type baseType = _baseType;
        return baseType == NullType ? null : baseType;
    }

    @Override
    public Type getGenericTypeDefinition() {
        return _genericTypeDefinition;
    }

    @Override
    public MemberType getMemberType() {
        return MemberType.TypeInfo;
    }

    @Override
    public Type getDeclaringType() {
        return _genericTypeDefinition.getDeclaringType();
    }

    @Override
    public final boolean isGenericType() {
        return true;
    }

    @Override
    public TypeBindings getTypeBindings() {
        return _typeBindings;
    }

    @Override
    int getModifiers() {
        return _genericTypeDefinition.getModifiers();
    }

    @Override
    public boolean isAnnotationPresent(final Class<? extends Annotation> annotationClass) {
        return _genericTypeDefinition.isAnnotationPresent(annotationClass);
    }

    @Override
    public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
        return _genericTypeDefinition.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return _genericTypeDefinition.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return _genericTypeDefinition.getDeclaredAnnotations();
    }

    @Override
    public <P, R> R accept(final TypeVisitor<P, R> typeVisitor, final P parameter) {
        return typeVisitor.visitClassType(this, parameter);
    }

    @Override
    public ConstructorList getDeclaredConstructors() {
        ensureConstructors();
        return _constructors;
    }

    @Override
    public MethodList getDeclaredMethods() {
        ensureMethods();
        return _methods;
    }

    @Override
    public FieldList getDeclaredFields() {
        ensureFields();
        return _fields;
    }

    @Override
    public TypeList getDeclaredTypes() {
        ensureNestedTypes();
        return _nestedTypes;
    }
}

