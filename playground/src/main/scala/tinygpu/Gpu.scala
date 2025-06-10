package tinygpu

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.Parameters



class GpuLazyModule(
) (implicit p: Parameters) extends LazyModule {
 // Access the parameters directly from the implicit 'p' object
  // val DATA_MEM_ADDR_BITS = p(GpuKeys.DataMemAddrBits)
  // val DATA_MEM_DATA_BITS = p(GpuKeys.DataMemDataBits)
  // val DATA_MEM_NUM_CHANNELS = p(GpuKeys.DataMemNumChannels)
  // val PROGRAM_MEM_ADDR_BITS = p(GpuKeys.ProgramMemAddrBits)
  // val PROGRAM_MEM_DATA_BITS = p(GpuKeys.ProgramMemDataBits)
  // val PROGRAM_MEM_NUM_CHANNELS = p(GpuKeys.ProgramMemNumChannels)
  // val NUM_CORES = p(GpuKeys.NumCores)
  // val THREADS_PER_BLOCK = p(GpuKeys.ThreadsPerBlock)

  lazy val module = new GpuModuleImpl(this)

  class GpuModuleImpl(outer: GpuLazyModule)(implicit p: Parameters)
    extends LazyModuleImp(outer) { //
    val DATA_MEM_ADDR_BITS = p(GpuKeys.DataMemAddrBits)
    val DATA_MEM_DATA_BITS = p(GpuKeys.DataMemDataBits)
    val DATA_MEM_NUM_CHANNELS = p(GpuKeys.DataMemNumChannels)
    val PROGRAM_MEM_ADDR_BITS = p(GpuKeys.ProgramMemAddrBits)
    val PROGRAM_MEM_DATA_BITS = p(GpuKeys.ProgramMemDataBits)
    val PROGRAM_MEM_NUM_CHANNELS = p(GpuKeys.ProgramMemNumChannels)
    val NUM_CORES = p(GpuKeys.NumCores)
    val THREADS_PER_BLOCK = p(GpuKeys.ThreadsPerBlock)

    val io = IO(new Bundle {
      // Kernel Execution
      val start  = Input(Bool())
      val done   = Output(Bool())

      // Device Control Register
      val device_control_write_enable = Input(Bool())
      val device_control_data        = Input(UInt(8.W))

      // Program Memory
      val program_mem_read_valid    = Output(Vec(PROGRAM_MEM_NUM_CHANNELS, Bool()))
      val program_mem_read_address  = Output(Vec(PROGRAM_MEM_NUM_CHANNELS, UInt(PROGRAM_MEM_ADDR_BITS.W)))
      val program_mem_read_ready    = Input(Vec(PROGRAM_MEM_NUM_CHANNELS, Bool()))
      val program_mem_read_data     = Input(Vec(PROGRAM_MEM_NUM_CHANNELS, UInt(PROGRAM_MEM_DATA_BITS.W)))

      // Data Memory
      val data_mem_read_valid    = Output(Vec(DATA_MEM_NUM_CHANNELS, Bool()))
      val data_mem_read_address  = Output(Vec(DATA_MEM_NUM_CHANNELS, UInt(DATA_MEM_ADDR_BITS.W)))
      val data_mem_read_ready    = Input(Vec(DATA_MEM_NUM_CHANNELS, Bool()))
      val data_mem_read_data     = Input(Vec(DATA_MEM_NUM_CHANNELS, UInt(DATA_MEM_DATA_BITS.W)))
      val data_mem_write_valid   = Output(Vec(DATA_MEM_NUM_CHANNELS, Bool()))
      val data_mem_write_address = Output(Vec(DATA_MEM_NUM_CHANNELS, UInt(DATA_MEM_ADDR_BITS.W)))
      val data_mem_write_data    = Output(Vec(DATA_MEM_NUM_CHANNELS, UInt(DATA_MEM_DATA_BITS.W)))
      val data_mem_write_ready   = Input(Vec(DATA_MEM_NUM_CHANNELS, Bool()))
    })

    // Control
    val thread_count = Wire(UInt(8.W))

    // Compute Core State
    val core_start        = Wire(Vec(NUM_CORES, Bool()))
    val core_reset        = Wire(Vec(NUM_CORES, Bool()))
    val core_done         = Wire(Vec(NUM_CORES, Bool()))
    val core_block_id     = Wire(Vec(NUM_CORES, UInt(8.W)))
    val core_thread_count = Wire(Vec(NUM_CORES, UInt((log2Ceil(THREADS_PER_BLOCK)+1).W)))

    // LSU <> Data Memory Controller Channels
    val NUM_LSUS = NUM_CORES * THREADS_PER_BLOCK
    val lsu_read_valid    = Wire(Vec(NUM_LSUS, Bool()))
    val lsu_read_address  = Wire(Vec(NUM_LSUS, UInt(DATA_MEM_ADDR_BITS.W)))
    val lsu_read_ready    = Wire(Vec(NUM_LSUS, Bool()))
    val lsu_read_data     = Wire(Vec(NUM_LSUS, UInt(DATA_MEM_DATA_BITS.W)))
    val lsu_write_valid   = Wire(Vec(NUM_LSUS, Bool()))
    val lsu_write_address = Wire(Vec(NUM_LSUS, UInt(DATA_MEM_ADDR_BITS.W)))
    val lsu_write_data    = Wire(Vec(NUM_LSUS, UInt(DATA_MEM_DATA_BITS.W)))
    val lsu_write_ready   = Wire(Vec(NUM_LSUS, Bool()))

    // Fetcher <> Program Memory Controller Channels
    val NUM_FETCHERS = NUM_CORES
    val fetcher_read_valid    = Wire(Vec(NUM_FETCHERS, Bool()))
    val fetcher_read_address  = Wire(Vec(NUM_FETCHERS, UInt(PROGRAM_MEM_ADDR_BITS.W)))
    val fetcher_read_ready    = Wire(Vec(NUM_FETCHERS, Bool()))
    val fetcher_read_data     = Wire(Vec(NUM_FETCHERS, UInt(PROGRAM_MEM_DATA_BITS.W)))

    // Device Control Register
    val dcr = Module(new DCR)
    dcr.io.device_control_write_enable := io.device_control_write_enable
    dcr.io.device_control_data := io.device_control_data
    thread_count := dcr.io.thread_count

    // Data Memory Controller
    val data_memory_controller = Module(new Controller(
      DATA_MEM_ADDR_BITS, DATA_MEM_DATA_BITS, NUM_LSUS, DATA_MEM_NUM_CHANNELS, true
    ))
    // data_memory_controller.io.clk := clock
    // data_memory_controller.io.reset := reset.asBool
    data_memory_controller.io.consumer_read_valid    := lsu_read_valid
    data_memory_controller.io.consumer_read_address  := lsu_read_address
    lsu_read_ready  := data_memory_controller.io.consumer_read_ready
    lsu_read_data   := data_memory_controller.io.consumer_read_data
    data_memory_controller.io.consumer_write_valid   := lsu_write_valid
    data_memory_controller.io.consumer_write_address := lsu_write_address
    data_memory_controller.io.consumer_write_data    := lsu_write_data
    lsu_write_ready := data_memory_controller.io.consumer_write_ready
    io.data_mem_read_valid    := data_memory_controller.io.mem_read_valid
    io.data_mem_read_address  := data_memory_controller.io.mem_read_address
    data_memory_controller.io.mem_read_ready := io.data_mem_read_ready
    data_memory_controller.io.mem_read_data  := io.data_mem_read_data
    io.data_mem_write_valid   := data_memory_controller.io.mem_write_valid
    io.data_mem_write_address := data_memory_controller.io.mem_write_address
    io.data_mem_write_data    := data_memory_controller.io.mem_write_data
    data_memory_controller.io.mem_write_ready := io.data_mem_write_ready

    // Program Memory Controller
    val program_memory_controller = Module(new Controller(
      PROGRAM_MEM_ADDR_BITS, PROGRAM_MEM_DATA_BITS, NUM_FETCHERS, PROGRAM_MEM_NUM_CHANNELS, false
    ))
    // program_memory_controller.io.clk := clock
    // program_memory_controller.io.reset := reset.asBool
    program_memory_controller.io.consumer_read_valid    := fetcher_read_valid
    program_memory_controller.io.consumer_read_address  := fetcher_read_address
    fetcher_read_ready := program_memory_controller.io.consumer_read_ready
    fetcher_read_data  := program_memory_controller.io.consumer_read_data
    program_memory_controller.io.consumer_write_valid   := VecInit(Seq.fill(NUM_FETCHERS)(false.B))
    program_memory_controller.io.consumer_write_address := VecInit(Seq.fill(NUM_FETCHERS)(0.U(PROGRAM_MEM_ADDR_BITS.W)))
    program_memory_controller.io.consumer_write_data    := VecInit(Seq.fill(NUM_FETCHERS)(0.U(PROGRAM_MEM_DATA_BITS.W)))
    program_memory_controller.io.mem_write_ready        := VecInit(Seq.fill(PROGRAM_MEM_NUM_CHANNELS)(false.B))
    io.program_mem_read_valid    := program_memory_controller.io.mem_read_valid
    io.program_mem_read_address  := program_memory_controller.io.mem_read_address
    program_memory_controller.io.mem_read_ready := io.program_mem_read_ready
    program_memory_controller.io.mem_read_data  := io.program_mem_read_data
    // io.program_mem_read_data := io.program_mem_read_data

    // Dispatcher
    val dispatcher = Module(new Dispatch(NUM_CORES, THREADS_PER_BLOCK))
    // dispatcher.io.clk := clock
    // dispatcher.io.reset := reset.asBool
    dispatcher.io.start := io.start
    dispatcher.io.thread_count := thread_count
    dispatcher.io.core_done := core_done
    core_start := dispatcher.io.core_start
    core_reset := dispatcher.io.core_reset
    core_block_id := dispatcher.io.core_block_id
    core_thread_count := dispatcher.io.core_thread_count
    io.done := dispatcher.io.done

    // Compute Cores
    for (i <- 0 until NUM_CORES) {
      // LSU信号分组
      val core_lsu_read_valid    = Wire(Vec(THREADS_PER_BLOCK, Bool()))
      val core_lsu_read_address  = Wire(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_ADDR_BITS.W)))
      val core_lsu_read_ready    = Wire(Vec(THREADS_PER_BLOCK, Bool()))
      val core_lsu_read_data     = Wire(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_DATA_BITS.W)))
      val core_lsu_write_valid   = Wire(Vec(THREADS_PER_BLOCK, Bool()))
      val core_lsu_write_address = Wire(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_ADDR_BITS.W)))
      val core_lsu_write_data    = Wire(Vec(THREADS_PER_BLOCK, UInt(DATA_MEM_DATA_BITS.W)))
      val core_lsu_write_ready   = Wire(Vec(THREADS_PER_BLOCK, Bool()))

      // LSU信号与全局信号连接
      for (j <- 0 until THREADS_PER_BLOCK) {
        val lsu_index = i * THREADS_PER_BLOCK + j
        lsu_read_valid(lsu_index)    := core_lsu_read_valid(j)
        lsu_read_address(lsu_index)  := core_lsu_read_address(j)
        core_lsu_read_ready(j)       := lsu_read_ready(lsu_index)
        core_lsu_read_data(j)        := lsu_read_data(lsu_index)
        lsu_write_valid(lsu_index)   := core_lsu_write_valid(j)
        lsu_write_address(lsu_index) := core_lsu_write_address(j)
        lsu_write_data(lsu_index)    := core_lsu_write_data(j)
        core_lsu_write_ready(j)      := lsu_write_ready(lsu_index)
      }

      // Compute Core
      val core = Module(new Core(
        DATA_MEM_ADDR_BITS, DATA_MEM_DATA_BITS,
        PROGRAM_MEM_ADDR_BITS, PROGRAM_MEM_DATA_BITS,
        THREADS_PER_BLOCK
      ))
      // core.io.clk := clock
      // core.io.reset := core_reset(i)
      core.io.start := core_start(i)
      core_done(i) := core.io.done
      core.io.block_id := core_block_id(i)
      core.io.thread_count := core_thread_count(i)
      fetcher_read_valid(i) := core.io.program_mem_read_valid
      fetcher_read_address(i) := core.io.program_mem_read_address
      core.io.program_mem_read_ready := fetcher_read_ready(i)
      core.io.program_mem_read_data := fetcher_read_data(i)
      core_lsu_read_valid := core.io.data_mem_read_valid
      core_lsu_read_address := core.io.data_mem_read_address
      core.io.data_mem_read_ready := core_lsu_read_ready
      core.io.data_mem_read_data := core_lsu_read_data
      core_lsu_write_valid := core.io.data_mem_write_valid
      core_lsu_write_address := core.io.data_mem_write_address
      core_lsu_write_data := core.io.data_mem_write_data 
      core.io.data_mem_write_ready := core_lsu_write_ready
    }
  }

}