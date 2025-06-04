// 文件名: ALU.scala
package tinygpu

import chisel3._
import chisel3.util._

class ALU extends Module {
  val io = IO(new Bundle {
    // val clk = Input(Clock()) // Chisel自动有clock, 可省略
    // val reset = Input(Bool())
    val enable = Input(Bool())
    val core_state = Input(UInt(3.W))
    val decoded_alu_arithmetic_mux = Input(UInt(2.W))
    val decoded_alu_output_mux = Input(Bool())
    val rs = Input(UInt(8.W))
    val rt = Input(UInt(8.W))
    val alu_out = Output(UInt(8.W))
  })

  // 定义操作码
  val ADD = 0.U(2.W)
  val SUB = 1.U(2.W)
  val MUL = 2.U(2.W)
  val DIV = 3.U(2.W)

  val alu_out_reg = RegInit(0.U(8.W))

  // Chisel推荐用when/elsewhen/otherwise
  when (reset.asBool) {
    alu_out_reg := 0.U
  } .elsewhen (io.enable) {
    // core_state == 3'b101
    when (io.core_state === "b101".U) {
      when (io.decoded_alu_output_mux) {
        // alu_out[2:0] = {gt, eq, lt}
        val diff = io.rs - io.rt
        val gt = (diff > 0.U)
        val eq = (diff === 0.U)
        val lt = (diff < 0.U)
        alu_out_reg := Cat(0.U(5.W), gt, eq, lt)
      } .otherwise {
        switch (io.decoded_alu_arithmetic_mux) {
          is (ADD) { alu_out_reg := io.rs + io.rt }
          is (SUB) { alu_out_reg := io.rs - io.rt }
          is (MUL) { alu_out_reg := io.rs * io.rt }
          is (DIV) { alu_out_reg := Mux(io.rt =/= 0.U, io.rs / io.rt, 0.U) } // 防止除0
        }
      }
    }
  }

  io.alu_out := alu_out_reg
}