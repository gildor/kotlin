/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import java.util.*

abstract class DelegatingSimpleType : SimpleType() {
    protected abstract val delegate: SimpleType

    override val annotations: Annotations get() = delegate.annotations
    override val constructor: TypeConstructor get() = delegate.constructor
    override val arguments: List<TypeProjection> get() = delegate.arguments
    override val isMarkedNullable: Boolean get() = delegate.isMarkedNullable
    override val memberScope: MemberScope get() = delegate.memberScope
}

class AbbreviatedType(override val delegate: SimpleType, val abbreviation: SimpleType) : DelegatingSimpleType() {
    val expandedType: SimpleType get() = delegate

    override fun replaceAnnotations(newAnnotations: Annotations)
            = AbbreviatedType(delegate.replaceAnnotations(newAnnotations), abbreviation)

    override fun makeNullableAsSpecified(newNullability: Boolean)
            = AbbreviatedType(delegate.makeNullableAsSpecified(newNullability), abbreviation.makeNullableAsSpecified(newNullability))

    override val isError: Boolean get() = false
}

fun KotlinType.getAbbreviatedType(): AbbreviatedType? = unwrap() as? AbbreviatedType

fun SimpleType.withAbbreviation(abbreviatedType: SimpleType): SimpleType {
    if (isError) return this
    return AbbreviatedType(this, abbreviatedType)
}

class FunctionType(
        override val delegate: SimpleType,
        /**
         * SpecialNames.NO_NAME_PROVIDED if no parameter name specified
         */
        val parameterNames: List<Name>
) : DelegatingSimpleType() {

    override val memberScope = object: MemberScope by delegate.memberScope {
        private val cache = HashMap<FunctionInvokeDescriptor, FunctionInvokeDescriptor>(2)

        override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
            return delegate.memberScope.getContributedFunctions(name, location).replaceParameterNames()
        }

        override fun getContributedDescriptors(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
            return delegate.memberScope.getContributedDescriptors(kindFilter, nameFilter).replaceParameterNames()
        }

        private fun <TDescriptor : DeclarationDescriptor> Collection<TDescriptor>.replaceParameterNames(): List<TDescriptor>
                = map { it.replaceParameterNames() }

        private fun <TDescriptor : DeclarationDescriptor> TDescriptor.replaceParameterNames(): TDescriptor {
            if (this !is FunctionInvokeDescriptor) return this
            @Suppress("UNCHECKED_CAST")
            return cache.getOrPut(this) { this.replaceParameterNames(parameterNames) } as TDescriptor
        }
    }

    override fun replaceAnnotations(newAnnotations: Annotations)
            = FunctionType(delegate.replaceAnnotations(newAnnotations), parameterNames)

    override fun makeNullableAsSpecified(newNullability: Boolean)
            = FunctionType(delegate.makeNullableAsSpecified(newNullability), parameterNames)

    override val isError: Boolean get() = false
}

fun KotlinType.getParameterNamesFromFunctionType(): List<Name>? = (unwrap() as? FunctionType)?.parameterNames

class LazyWrappedType(storageManager: StorageManager, computation: () -> KotlinType): WrappedType() {
    private val lazyValue = storageManager.createLazyValue(computation)

    override val delegate: KotlinType get() = lazyValue()

    override fun isComputed(): Boolean = lazyValue.isComputed()
}