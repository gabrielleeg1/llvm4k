/*
 *    Copyright 2022 Plank
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.plank.llvm4k.ir

import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import llvm.LLVMGetTypeContext
import llvm.LLVMTypeKind
import llvm.LLVMTypeRef
import org.plank.llvm4k.Context
import org.plank.llvm4k.Owner
import org.plank.llvm4k.printToString
import org.plank.llvm4k.toInt

public actual sealed interface Type : Owner<LLVMTypeRef> {
  public actual val context: Context get() = Context(LLVMGetTypeContext(ref))
  public actual val isSized: Boolean get() = llvm.LLVMTypeIsSized(ref) == 1
  public actual val size: ConstantInt get() = ConstantInt(llvm.LLVMSizeOf(ref))
  public actual val align: ConstantInt get() = ConstantInt(llvm.LLVMAlignOf(ref))
  public actual val kind: Kind get() = Kind.byValue(llvm.LLVMGetTypeKind(ref).value)

  public actual fun pointer(addrSpace: AddrSpace): PointerType {
    return PointerType(this, addrSpace)
  }

  public actual override fun toString(): String

  public actual enum class Kind(public val llvm: LLVMTypeKind) {
    Void(LLVMTypeKind.LLVMVoidTypeKind),
    Half(LLVMTypeKind.LLVMHalfTypeKind),
    Float(LLVMTypeKind.LLVMFloatTypeKind),
    Double(LLVMTypeKind.LLVMDoubleTypeKind),
    X86_FP80(LLVMTypeKind.LLVMX86_FP80TypeKind),
    FP128(LLVMTypeKind.LLVMFP128TypeKind),
    PPC_FP128(LLVMTypeKind.LLVMPPC_FP128TypeKind),
    Label(LLVMTypeKind.LLVMLabelTypeKind),
    Integer(LLVMTypeKind.LLVMIntegerTypeKind),
    Function(LLVMTypeKind.LLVMFunctionTypeKind),
    Struct(LLVMTypeKind.LLVMStructTypeKind),
    Array(LLVMTypeKind.LLVMArrayTypeKind),
    Pointer(LLVMTypeKind.LLVMPointerTypeKind),
    Vector(LLVMTypeKind.LLVMVectorTypeKind),
    Metadata(LLVMTypeKind.LLVMMetadataTypeKind),
    X86_MMX(LLVMTypeKind.LLVMX86_MMXTypeKind),
    Token(LLVMTypeKind.LLVMTokenTypeKind),
    ScalableVector(LLVMTypeKind.LLVMScalableVectorTypeKind),
    BFloat(LLVMTypeKind.LLVMBFloatTypeKind),
    X86_AMX(LLVMTypeKind.LLVMX86_AMXTypeKind);

    public actual val value: UInt get() = llvm.value

    public actual companion object {
      public fun byValue(llvm: LLVMTypeKind): Kind {
        return byValue(llvm.value)
      }

      public actual fun byValue(value: Int): Kind {
        return byValue(value.toUInt())
      }

      public actual fun byValue(value: UInt): Kind {
        return values().single { it.value == value }
      }
    }
  }
}

public actual class StructType(public override val ref: LLVMTypeRef?) : Type {
  public actual val name: String? get() = llvm.LLVMGetStructName(ref)?.toKString()

  public actual val isPacked: Boolean get() = llvm.LLVMIsPackedStruct(ref) == 1
  public actual val isOpaque: Boolean get() = llvm.LLVMIsOpaqueStruct(ref) == 1
  public actual val isLiteral: Boolean get() = llvm.LLVMIsLiteralStruct(ref) == 1
  public actual val hasName: Boolean get() = name != null

  public actual var elements: List<Type>
    get(): List<Type> = memScoped {
      require(!isOpaque) { "Cannot get elements of opaque struct" }

      val size = llvm.LLVMCountStructElementTypes(ref).toInt()
      val arguments = allocArray<CPointerVarOf<LLVMTypeRef>>(size)

      llvm.LLVMGetStructElementTypes(ref, arguments)

      (0 until size).map { Type(arguments[it]) }
    }
    set(value): Unit {
      llvm.LLVMStructSetBody(
        ref,
        value.map { it.ref }.toCValues(),
        value.size.toUInt(),
        isPacked.toInt(),
      )
    }

  public actual val constantNull: ConstantAggregate
    get(): ConstantAggregate = ConstantAggregate(llvm.LLVMConstNull(ref))

  public actual fun getConstant(
    vararg elements: Constant,
    isPacked: Boolean,
  ): ConstantAggregate {
    val ref = when {
      name != null -> llvm.LLVMConstStructInContext(
        context.ref,
        elements.map { it.ref }.toCValues(),
        elements.size.toUInt(),
        isPacked.toInt(),
      )
      else -> {
        llvm.LLVMConstNamedStruct(ref, elements.map { it.ref }.toCValues(), elements.size.toUInt())
      }
    }

    return ConstantAggregate(ref)
  }

  public override fun toString(): String {
    return llvm.LLVMPrintTypeToString(ref)!!.toKString()
  }
}

public actual sealed class CompositeType : Type {
  public actual abstract val count: Int
  public actual val contained: Type get() = Type(llvm.LLVMGetElementType(ref))

  public actual val elements: List<Type>
    get() = memScoped {
      val arguments = allocArray<CPointerVarOf<LLVMTypeRef>>(count)

      llvm.LLVMGetSubtypes(ref, arguments)

      (0 until count).map { Type(arguments[it]) }
    }

  public actual override fun toString(): String = printToString()
}

public actual sealed class VectorType : CompositeType() {
  public override val count: Int get() = llvm.LLVMGetVectorSize(ref).toInt()

  public actual val constantNull: ConstantDataVector
    get() = ConstantDataVector(llvm.LLVMConstNull(ref))
}

public actual class FixedVectorType(public override val ref: LLVMTypeRef?) : VectorType() {
  public actual constructor(contained: Type, count: Int) :
    this(llvm.LLVMVectorType(contained.ref, count.toUInt()))
}

public actual class ScalableVectorType(public override val ref: LLVMTypeRef?) : VectorType() {
  public actual constructor(contained: Type, minCount: Int) :
    this(llvm.LLVMScalableVectorType(contained.ref, minCount.toUInt()))
}

public actual class ArrayType(public override val ref: LLVMTypeRef?) : CompositeType() {
  public override val count: Int get() = llvm.LLVMGetArrayLength(ref).toInt()

  public actual val constantNull: ConstantDataArray
    get() = ConstantDataArray(llvm.LLVMConstNull(ref))

  public actual constructor(contained: Type, count: Int) :
    this(llvm.LLVMArrayType(contained.ref, count.toUInt()))
}

public actual class PointerType(public override val ref: LLVMTypeRef?) : CompositeType() {
  public override val count: Int = 1

  public actual constructor(contained: Type, addrSpace: AddrSpace) :
    this(llvm.LLVMPointerType(contained.ref, addrSpace.value))
}

public actual class IntegerType(public override val ref: LLVMTypeRef?) : Type {
  public actual val constantNull: ConstantInt get() = ConstantInt(llvm.LLVMConstNull(ref))
  public actual val allOnes: ConstantInt get() = ConstantInt(llvm.LLVMConstAllOnes(ref))
  public actual val typeWidth: Int get() = llvm.LLVMGetIntTypeWidth(ref).toInt()

  public actual fun getConstant(value: Int, unsigned: Boolean): ConstantInt {
    return getConstant(value.toLong(), unsigned)
  }

  public actual fun getConstant(value: Long, unsigned: Boolean): ConstantInt {
    return ConstantInt(llvm.LLVMConstInt(ref, value.toULong(), unsigned.toInt()))
  }

  public actual override fun toString(): String = printToString()
}

public actual class FloatType(public override val ref: LLVMTypeRef?) : Type {
  public actual val constantNull: ConstantFP get() = ConstantFP(llvm.LLVMConstNull(ref))
  public actual val allOnes: ConstantFP get() = ConstantFP(llvm.LLVMConstAllOnes(ref))

  public actual fun getConstant(value: Float): ConstantFP {
    return getConstant(value.toDouble())
  }

  public actual fun getConstant(value: Double): ConstantFP {
    return ConstantFP(llvm.LLVMConstReal(ref, value))
  }

  public actual override fun toString(): String = printToString()
}

public actual class FunctionType(public override val ref: LLVMTypeRef?) : Type {
  public actual constructor(returnType: Type, params: List<Type>, isVarargs: Boolean) :
    this(
      llvm.LLVMFunctionType(
        returnType.ref,
        params.map { it.ref }.toCValues(),
        params.size.toUInt(),
        isVarargs.toInt()
      )
    )

  public actual val returnType: Type get() = Type(llvm.LLVMGetReturnType(ref))
  public actual val isVarargs: Boolean get() = llvm.LLVMIsFunctionVarArg(ref) == 1

  public actual val parameters: List<Type>
    get(): List<Type> = memScoped {
      val size = llvm.LLVMCountParamTypes(ref).toInt()
      val arguments = allocArray<CPointerVarOf<LLVMTypeRef>>(size)

      llvm.LLVMGetParamTypes(ref, arguments)

      (0 until size).map { Type(arguments[it]) }
    }

  public actual override fun toString(): String = printToString()
}

public actual sealed class StubType : Type {
  public actual override fun toString(): String = printToString()
}

public actual class VoidType(public override val ref: LLVMTypeRef?) : StubType()

public actual class LabelType(public override val ref: LLVMTypeRef?) : StubType()

public actual class MetadataType(public override val ref: LLVMTypeRef?) : StubType()

public actual class TokenType(public override val ref: LLVMTypeRef?) : StubType()

public actual class X86MMXType(public override val ref: LLVMTypeRef?) : StubType()

@Suppress("ComplexMethod")
public fun Type(ref: LLVMTypeRef?): Type {
  return when (Type.Kind.byValue(llvm.LLVMGetTypeKind(ref))) {
    Type.Kind.Void -> VoidType(ref)
    Type.Kind.Half -> FloatType(ref)
    Type.Kind.Float -> FloatType(ref)
    Type.Kind.Double -> FloatType(ref)
    Type.Kind.X86_FP80 -> FloatType(ref)
    Type.Kind.FP128 -> FloatType(ref)
    Type.Kind.PPC_FP128 -> FloatType(ref)
    Type.Kind.Label -> LabelType(ref)
    Type.Kind.Integer -> IntegerType(ref)
    Type.Kind.Function -> FunctionType(ref)
    Type.Kind.Struct -> StructType(ref)
    Type.Kind.Array -> ArrayType(ref)
    Type.Kind.Pointer -> PointerType(ref)
    Type.Kind.Vector -> FixedVectorType(ref)
    Type.Kind.Metadata -> MetadataType(ref)
    Type.Kind.X86_MMX -> X86MMXType(ref)
    Type.Kind.Token -> TokenType(ref)
    Type.Kind.ScalableVector -> ScalableVectorType(ref)
    Type.Kind.BFloat -> FloatType(ref)
    Type.Kind.X86_AMX -> FloatType(ref)
  }
}