package com.qrfiletransfer.utils

import java.math.BigInteger
import kotlin.math.pow

/**
 * Reed-Solomon 纠错码实现
 * 支持前向纠错，可以在二维码损坏时恢复数据
 */
class ReedSolomon(
    private val dataShards: Int,
    private val parityShards: Int,
    private val primitive: Int = 0x11d,
    private val generatorStart: Int = 0
) {
    
    companion object {
        // 预计算的伽罗华域对数表
        private val GF_LOG = IntArray(256)
        private val GF_EXP = IntArray(512)
        
        init {
            var x = 1
            for (i in 0 until 255) {
                GF_EXP[i] = x
                GF_LOG[x] = i
                x = x shl 1
                if (x and 0x100 != 0) {
                    x = x xor 0x11d
                }
            }
            
            for (i in 255 until 512) {
                GF_EXP[i] = GF_EXP[i - 255]
            }
        }
        
        private fun gfMul(a: Int, b: Int): Int {
            if (a == 0 || b == 0) return 0
            return GF_EXP[GF_LOG[a] + GF_LOG[b]]
        }
        
        private fun gfDiv(a: Int, b: Int): Int {
            if (a == 0) return 0
            if (b == 0) throw ArithmeticException("Divide by zero")
            return GF_EXP[(GF_LOG[a] - GF_LOG[b] + 255) % 255]
        }
        
        private fun gfPow(a: Int, power: Int): Int {
            if (power == 0) return 1
            if (a == 0) return 0
            return GF_EXP[(GF_LOG[a] * power) % 255]
        }
    }
    
    private val totalShards = dataShards + parityShards
    
    private val generatorPolynomial: IntArray by lazy {
        var g = intArrayOf(1)
        
        for (i in generatorStart until generatorStart + parityShards) {
            val a = GF_EXP[i]
            val gNew = IntArray(g.size + 1)
            
            for (j in g.indices) {
                gNew[j] = gfMul(g[j], a)
            }
            
            for (j in g.indices) {
                gNew[j + 1] = gNew[j + 1] xor g[j]
            }
            
            g = gNew
        }
        
        g
    }
    
    /**
     * 编码数据，生成校验块
     */
    fun encode(data: Array<ByteArray?>): Array<ByteArray?> {
        if (data.size != dataShards) {
            throw IllegalArgumentException("数据块数量必须为 $dataShards")
        }
        
        val shardSize = data.map { it?.size ?: 0 }.maxOrNull() ?: 0
        
        val allShards = Array<ByteArray?>(totalShards) { null }
        
        for (i in 0 until dataShards) {
            allShards[i] = ByteArray(shardSize)
            data[i]?.copyInto(allShards[i]!!)
        }
        
        for (i in dataShards until totalShards) {
            allShards[i] = ByteArray(shardSize)
        }
        
        val parity = Array(parityShards) { ByteArray(shardSize) }
        val coeffs = Array(dataShards) { ByteArray(shardSize) }
        
        for (i in 0 until dataShards) {
            data[i]?.copyInto(coeffs[i])
        }
        
        for (i in 0 until shardSize) {
            val column = ByteArray(dataShards)
            for (j in 0 until dataShards) {
                column[j] = coeffs[j][i]
            }
            
            val parityColumn = encodeColumn(column)
            
            for (j in 0 until parityShards) {
                parity[j][i] = parityColumn[j]
            }
        }
        
        for (i in 0 until parityShards) {
            parity[i].copyInto(allShards[dataShards + i]!!)
        }
        
        return allShards
    }
    
    /**
     * 解码数据，尝试恢复损坏的块
     */
    fun decode(shards: Array<ByteArray?>, shardPresent: BooleanArray): Array<ByteArray?> {
        if (shards.size != totalShards) {
            throw IllegalArgumentException("总块数必须为 $totalShards")
        }
        
        val missingCount = shardPresent.count { !it }
        if (missingCount == 0) {
            return shards
        }
        
        if (missingCount > parityShards) {
            throw IllegalArgumentException("损坏的块太多，无法恢复")
        }
        
        val shardSize = shards.firstNotNullOfOrNull { it?.size } ?: 0
        
        val dataPresent = shardPresent.copyOfRange(0, dataShards)
        
        val matrix = buildMatrix(dataPresent)
        
        val result = shards.copyOf()
        
        for (i in 0 until shardSize) {
            val column = ByteArray(totalShards)
            for (j in 0 until totalShards) {
                column[j] = shards[j]?.get(i) ?: 0
            }
            
            val decodedColumn = decodeColumn(column, matrix, shardPresent)
            
            for (j in 0 until totalShards) {
                if (shards[j] != null) {
                    shards[j]!![i] = decodedColumn[j]
                }
            }
        }
        
        return result
    }
    
    private fun encodeColumn(data: ByteArray): ByteArray {
        if (data.size != dataShards) {
            throw IllegalArgumentException("列数据大小必须为 $dataShards")
        }
        
        val result = ByteArray(parityShards)
        
        for (i in 0 until parityShards) {
            var sum = 0
            
            for (j in 0 until dataShards) {
                sum = sum xor gfMul(data[j].toInt() and 0xFF, generatorPolynomial[i])
            }
            
            result[i] = sum.toByte()
        }
        
        return result
    }
    
    private fun decodeColumn(
        column: ByteArray,
        matrix: Array<IntArray>,
        present: BooleanArray
    ): ByteArray {
        val subMatrix = buildSubMatrix(matrix, present)
        val inverted = invertMatrix(subMatrix)
        
        val result = ByteArray(totalShards)
        
        for (i in 0 until totalShards) {
            if (present[i]) {
                result[i] = column[i]
            } else {
                var sum = 0
                for (j in 0 until totalShards) {
                    if (present[j]) {
                        sum = sum xor gfMul(column[j].toInt() and 0xFF, inverted[i][j])
                    }
                }
                result[i] = sum.toByte()
            }
        }
        
        return result
    }
    
    private fun buildMatrix(dataPresent: BooleanArray): Array<IntArray> {
        val matrix = Array(totalShards) { IntArray(totalShards) }
        
        for (i in 0 until totalShards) {
            val exp = if (i < dataShards) 0 else i - dataShards + generatorStart
            
            var coeff = 1
            for (j in 0 until totalShards) {
                matrix[i][j] = coeff
                coeff = gfMul(coeff, exp)
            }
        }
        
        return matrix
    }
    
    private fun buildSubMatrix(matrix: Array<IntArray>, present: BooleanArray): Array<IntArray> {
        val size = present.count { it }
        val subMatrix = Array(size) { IntArray(size) }
        
        var rowIdx = 0
        for (i in 0 until totalShards) {
            if (present[i]) {
                var colIdx = 0
                for (j in 0 until totalShards) {
                    if (present[j]) {
                        subMatrix[rowIdx][colIdx] = matrix[i][j]
                        colIdx++
                    }
                }
                rowIdx++
            }
        }
        
        return subMatrix
    }
    
    private fun invertMatrix(matrix: Array<IntArray>): Array<IntArray> {
        val size = matrix.size
        val augmented = Array(size) { row ->
            IntArray(size * 2).apply {
                for (col in 0 until size) {
                    this[col] = matrix[row][col]
                }
                this[size + row] = 1
            }
        }
        
        for (i in 0 until size) {
            var pivot = i
            while (pivot < size && augmented[pivot][i] == 0) {
                pivot++
            }
            
            if (pivot == size) {
                throw IllegalArgumentException("矩阵不可逆")
            }
            
            if (pivot != i) {
                val temp = augmented[i]
                augmented[i] = augmented[pivot]
                augmented[pivot] = temp
            }
            
            val pivotVal = augmented[i][i]
            if (pivotVal != 1) {
                val scale = gfDiv(1, pivotVal)
                for (j in i until size * 2) {
                    augmented[i][j] = gfMul(augmented[i][j], scale)
                }
            }
            
            for (j in 0 until size) {
                if (j != i && augmented[j][i] != 0) {
                    val factor = augmented[j][i]
                    for (k in i until size * 2) {
                        augmented[j][k] = augmented[j][k] xor gfMul(augmented[i][k], factor)
                    }
                }
            }
        }
        
        return Array(size) { row ->
            IntArray(size) { col ->
                augmented[row][size + col]
            }
        }
    }
    
    /**
     * 计算可以恢复的最大错误数
     */
    fun maxCorrectableErrors(): Int {
        return parityShards / 2
    }
    
    /**
     * 计算可以恢复的最大丢失数
     */
    fun maxCorrectableErasures(): Int {
        return parityShards
    }
}
