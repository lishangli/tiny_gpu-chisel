// 文件名: Core.scala
package tinygpu

import chisel3._
import chisel3.util._

class Core(
  val DATA_MEM_ADDR_BITS: Int = 8,
  val DATA_MEM_DATA_BITS: Int = 8,
  val PROGRAM_MEM_ADDR_BITS: Int = 8,
  val PROGRAM_MEM_DATA_BITS: Int = 16,
  val THREADS_PER_BLOCK: Int = 4
) extends Module {
  val io = IO(new Bundle {
    // Kernel Execution
    val start  = Input(Bool())
    val done   = Output(Bool())

    // Block Metadata
    val block_id     = Input(UInt(8.W))
    val thread_count = Input(UInt((log2Ceil(THREADS_PER_BLOCK)+1).W))

    // Program Memory
    val program_mem_read_valid   = Output(Bool())
    val program_mem_read_address = Output(UInt(PROGRAM_MEM_ADDR_BITS.W))
    val program_mem_read_ready   = Input(Bool())
    val program_mem_read_data    = Input(UInt(PROGRAM_MEM_DATA_BITS.W))

    // Data Memory
    val data_mem_read_valid   = Output(Vec(THREADS_PER_BLOCK, Bool()))
    val data_mem_read_address = Output(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_ADDR_BITS.W)))
    val data_mem_read_ready   = Input(Vec(THREADS_PER_BLOCK, Bool()))
    val data_mem_read_data    = Input(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_DATA_BITS.W)))
    val data_mem_write_valid  = Output(Vec(THREADS_PER_BLOCK, Bool()))
    val data_mem_write_address= Output(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_ADDR_BITS.W)))
    val data_mem_write_data   = Output(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_DATA_BITS.W)))
    val data_mem_write_ready  = Input(Vec(THREADS_PER_BLOCK, Bool()))
  })

  // State
  val core_state    = RegInit(0.U(3.W))
  val fetcher_state = RegInit(0.U(3.W))
  val instruction   = RegInit(0.U(16.W))

  // Intermediate Signals
  val current_pc = RegInit(0.U(8.W))
  val next_pc    = Wire(Vec(THREADS_PER_BLOCK, UInt(8.W)))
  val rs         = Wire(Vec(THREADS_PER_BLOCK, UInt(8.W)))
  val rt         = Wire(Vec(THREADS_PER_BLOCK, UInt(8.W)))
  val lsu_state  = Wire(Vec(THREADS_PER_BLOCK, UInt(2.W)))
  val lsu_out    = Wire(Vec(THREADS_PER_BLOCK, UInt(8.W)))
  val alu_out    = Wire(Vec(THREADS_PER_BLOCK, UInt(8.W)))

  // Decoded Instruction Signals
  val decoded_rd_address = Wire(UInt(4.W))
  val decoded_rs_address = Wire(UInt(4.W))
  val decoded_rt_address = Wire(UInt(4.W))
  val decoded_nzp       = Wire(UInt(3.W))
  val decoded_immediate = Wire(UInt(8.W))

  // Decoded Control Signals
  val decoded_reg_write_enable     = Wire(Bool())
  val decoded_mem_read_enable      = Wire(Bool())
  val decoded_mem_write_enable     = Wire(Bool())
  val decoded_nzp_write_enable     = Wire(Bool())
  val decoded_reg_input_mux        = Wire(UInt(2.W))
  val decoded_alu_arithmetic_mux   = Wire(UInt(2.W))
  val decoded_alu_output_mux       = Wire(Bool())
  val decoded_pc_mux               = Wire(Bool())
  val decoded_ret                  = Wire(Bool())

  // Fetcher
  val fetcher = Module(new Fetcher(PROGRAM_MEM_ADDR_BITS, PROGRAM_MEM_DATA_BITS))
  // fetcher.io.clk                := clock
  // fetcher.io.reset              := reset.asBool
  fetcher.io.core_state         := core_state
  fetcher.io.current_pc         := current_pc
  io.program_mem_read_valid     := fetcher.io.mem_read_valid
  io.program_mem_read_address   := fetcher.io.mem_read_address
  fetcher.io.mem_read_ready     := io.program_mem_read_ready
  fetcher.io.mem_read_data      := io.program_mem_read_data
  fetcher_state                 := fetcher.io.fetcher_state
  instruction                   := fetcher.io.instruction

  // Decoder
  val decoder = Module(new Decoder)
  // decoder.io.clk                := clock
  // decoder.io.reset              := reset.asBool
  decoder.io.core_state         := core_state
  decoder.io.instruction        := instruction
  decoded_rd_address            := decoder.io.decoded_rd_address
  decoded_rs_address            := decoder.io.decoded_rs_address
  decoded_rt_address            := decoder.io.decoded_rt_address
  decoded_nzp                   := decoder.io.decoded_nzp
  decoded_immediate             := decoder.io.decoded_immediate
  decoded_reg_write_enable      := decoder.io.decoded_reg_write_enable
  decoded_mem_read_enable       := decoder.io.decoded_mem_read_enable
  decoded_mem_write_enable      := decoder.io.decoded_mem_write_enable
  decoded_nzp_write_enable      := decoder.io.decoded_nzp_write_enable
  decoded_reg_input_mux         := decoder.io.decoded_reg_input_mux
  decoded_alu_arithmetic_mux    := decoder.io.decoded_alu_arithmetic_mux
  decoded_alu_output_mux        := decoder.io.decoded_alu_output_mux
  decoded_pc_mux                := decoder.io.decoded_pc_mux
  decoded_ret                   := decoder.io.decoded_ret

  // Scheduler
  val scheduler = Module(new Scheduler(THREADS_PER_BLOCK))
  // scheduler.io.clk                    := clock
  // scheduler.io.reset                  := reset.asBool
  scheduler.io.start                  := io.start
  scheduler.io.fetcher_state          := fetcher_state
  core_state  := scheduler.io.core_state 
  scheduler.io.decoded_mem_read_enable:= decoded_mem_read_enable
  scheduler.io.decoded_mem_write_enable:= decoded_mem_write_enable
  scheduler.io.decoded_ret            := decoded_ret
  scheduler.io.lsu_state              := lsu_state
  current_pc := scheduler.io.current_pc
  scheduler.io.next_pc := next_pc
  io.done                             := scheduler.io.done

  // 线程实例
  for (i <- 0 until THREADS_PER_BLOCK) {
    // ALU
    val alu = Module(new ALU)
    // alu.io.clk                       := clock
    // alu.io.reset                     := reset.asBool
    alu.io.enable                    := (i.U < io.thread_count)
    alu.io.core_state                := core_state
    alu.io.decoded_alu_arithmetic_mux:= decoded_alu_arithmetic_mux
    alu.io.decoded_alu_output_mux    := decoded_alu_output_mux
    alu.io.rs                        := rs(i)
    alu.io.rt                        := rt(i)
    alu_out(i)                       := alu.io.alu_out

    // LSU
    val lsu = Module(new LSU)
    // lsu.io.clk                       := clock
    // lsu.io.reset                     := reset.asBool
    lsu.io.enable                    := (i.U < io.thread_count)
    lsu.io.core_state                := core_state
    lsu.io.decoded_mem_read_enable   := decoded_mem_read_enable
    lsu.io.decoded_mem_write_enable  := decoded_mem_write_enable
    io.data_mem_read_valid(i)        := lsu.io.mem_read_valid
    io.data_mem_read_address(i)      := lsu.io.mem_read_address
    lsu.io.mem_read_ready            := io.data_mem_read_ready(i)
    lsu.io.mem_read_data             := io.data_mem_read_data(i)
    io.data_mem_write_valid(i)       := lsu.io.mem_write_valid
    io.data_mem_write_address(i)     := lsu.io.mem_write_address
    io.data_mem_write_data(i)        := lsu.io.mem_write_data
    lsu.io.mem_write_ready           := io.data_mem_write_ready(i)
    lsu.io.rs                        := rs(i)
    lsu.io.rt                        := rt(i)
    lsu_state(i)                     := lsu.io.lsu_state
    lsu_out(i)                       := lsu.io.lsu_out

    // Register File
    val regfile = Module(new Registers(THREADS_PER_BLOCK, i, DATA_MEM_DATA_BITS))
    // regfile.io.clk                   := clock
    // regfile.io.reset                 := reset.asBool
    regfile.io.enable                := (i.U < io.thread_count)
    regfile.io.block_id              := io.block_id
    regfile.io.core_state            := core_state
    regfile.io.decoded_reg_write_enable := decoded_reg_write_enable
    regfile.io.decoded_reg_input_mux := decoded_reg_input_mux
    regfile.io.decoded_rd_address    := decoded_rd_address
    regfile.io.decoded_rs_address    := decoded_rs_address
    regfile.io.decoded_rt_address    := decoded_rt_address
    regfile.io.decoded_immediate     := decoded_immediate
    regfile.io.alu_out               := alu_out(i)
    regfile.io.lsu_out               := lsu_out(i)
    rs(i)                            := regfile.io.rs
    rt(i)                            := regfile.io.rt

    // Program Counter
    val pcmod = Module(new PC(DATA_MEM_DATA_BITS, PROGRAM_MEM_ADDR_BITS))
    // pcmod.io.clk                     := clock
    // pcmod.io.reset                   := reset.asBool
    pcmod.io.enable                  := (i.U < io.thread_count)
    pcmod.io.core_state              := core_state
    pcmod.io.decoded_nzp             := decoded_nzp
    pcmod.io.decoded_immediate       := decoded_immediate
    pcmod.io.decoded_nzp_write_enable:= decoded_nzp_write_enable
    pcmod.io.decoded_pc_mux          := decoded_pc_mux
    pcmod.io.alu_out                 := alu_out(i)
    pcmod.io.current_pc              := current_pc
    next_pc(i)                       := pcmod.io.next_pc
  }
}