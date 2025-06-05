package tinygpu

import chisel3._
import chisel3.util._

class Decoder extends Module {
  val io = IO(new Bundle {
    val core_state = Input(UInt(3.W))
    val instruction = Input(UInt(16.W))

    // Instruction Signals
    val decoded_rd_address = Output(UInt(4.W))
    val decoded_rs_address = Output(UInt(4.W))
    val decoded_rt_address = Output(UInt(4.W))
    val decoded_nzp = Output(UInt(3.W))
    val decoded_immediate = Output(UInt(8.W))

    // Control Signals
    val decoded_reg_write_enable = Output(Bool())
    val decoded_mem_read_enable = Output(Bool())
    val decoded_mem_write_enable = Output(Bool())
    val decoded_nzp_write_enable = Output(Bool())
    val decoded_reg_input_mux = Output(UInt(2.W))
    val decoded_alu_arithmetic_mux = Output(UInt(2.W))
    val decoded_alu_output_mux = Output(Bool())
    val decoded_pc_mux = Output(Bool())
    val decoded_ret = Output(Bool())
  })

  // 指令类型
  val NOP   = "b0000".U(4.W)
  val BRnzp = "b0001".U(4.W)
  val CMP   = "b0010".U(4.W)
  val ADD   = "b0011".U(4.W)
  val SUB   = "b0100".U(4.W)
  val MUL   = "b0101".U(4.W)
  val DIV   = "b0110".U(4.W)
  val LDR   = "b0111".U(4.W)
  val STR   = "b1000".U(4.W)
  val CONST = "b1001".U(4.W)
  val RET   = "b1111".U(4.W)

  // 默认输出
  val rd_address = WireDefault(0.U(4.W))
  val rs_address = WireDefault(0.U(4.W))
  val rt_address = WireDefault(0.U(4.W))
  val immediate  = WireDefault(0.U(8.W))
  val nzp        = WireDefault(0.U(3.W))

  val reg_write_enable     = WireDefault(false.B)
  val mem_read_enable      = WireDefault(false.B)
  val mem_write_enable     = WireDefault(false.B)
  val nzp_write_enable     = WireDefault(false.B)
  val reg_input_mux        = WireDefault(0.U(2.W))
  val alu_arithmetic_mux   = WireDefault(0.U(2.W))
  val alu_output_mux       = WireDefault(false.B)
  val pc_mux               = WireDefault(false.B)
  val ret                  = WireDefault(false.B)

  when (reset.asBool) {
    // 全部清零
    io.decoded_rd_address := 0.U
    io.decoded_rs_address := 0.U
    io.decoded_rt_address := 0.U
    io.decoded_immediate  := 0.U
    io.decoded_nzp        := 0.U
    io.decoded_reg_write_enable := false.B
    io.decoded_mem_read_enable := false.B
    io.decoded_mem_write_enable := false.B
    io.decoded_nzp_write_enable := false.B
    io.decoded_reg_input_mux := 0.U
    io.decoded_alu_arithmetic_mux := 0.U
    io.decoded_alu_output_mux := false.B
    io.decoded_pc_mux := false.B
    io.decoded_ret := false.B
  } .elsewhen (io.core_state === "b010".U) {
    // 解码
    rd_address := io.instruction(11,8)
    rs_address := io.instruction(7,4)
    rt_address := io.instruction(3,0)
    immediate  := io.instruction(7,0)
    nzp        := io.instruction(11,9)

    switch(io.instruction(15,12)) {
      is (NOP)   { /* no-op */ }
      is (BRnzp) { pc_mux := true.B }
      is (CMP)   { alu_output_mux := true.B; nzp_write_enable := true.B }
      is (ADD)   { reg_write_enable := true.B; reg_input_mux := 0.U; alu_arithmetic_mux := 0.U }
      is (SUB)   { reg_write_enable := true.B; reg_input_mux := 0.U; alu_arithmetic_mux := 1.U }
      is (MUL)   { reg_write_enable := true.B; reg_input_mux := 0.U; alu_arithmetic_mux := 2.U }
      is (DIV)   { reg_write_enable := true.B; reg_input_mux := 0.U; alu_arithmetic_mux := 3.U }
      is (LDR)   { reg_write_enable := true.B; reg_input_mux := 1.U; mem_read_enable := true.B }
      is (STR)   { mem_write_enable := true.B }
      is (CONST) { reg_write_enable := true.B; reg_input_mux := 2.U }
      is (RET)   { ret := true.B }
    }

    io.decoded_rd_address := rd_address
    io.decoded_rs_address := rs_address
    io.decoded_rt_address := rt_address
    io.decoded_immediate  := immediate
    io.decoded_nzp        := nzp
    io.decoded_reg_write_enable := reg_write_enable
    io.decoded_mem_read_enable := mem_read_enable
    io.decoded_mem_write_enable := mem_write_enable
    io.decoded_nzp_write_enable := nzp_write_enable
    io.decoded_reg_input_mux := reg_input_mux
    io.decoded_alu_arithmetic_mux := alu_arithmetic_mux
    io.decoded_alu_output_mux := alu_output_mux
    io.decoded_pc_mux := pc_mux
    io.decoded_ret := ret
  } .otherwise {
    // 其它状态保持不变
    io.decoded_rd_address := 0.U
    io.decoded_rs_address := 0.U
    io.decoded_rt_address := 0.U
    io.decoded_immediate  := 0.U
    io.decoded_nzp        := 0.U
    io.decoded_reg_write_enable := false.B
    io.decoded_mem_read_enable := false.B
    io.decoded_mem_write_enable := false.B
    io.decoded_nzp_write_enable := false.B
    io.decoded_reg_input_mux := 0.U
    io.decoded_alu_arithmetic_mux := 0.U
    io.decoded_alu_output_mux := false.B
    io.decoded_pc_mux := false.B
    io.decoded_ret := false.B
  }
}