package rle

import Chisel._
import chisel3.{Printable}
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig}
import freechips.rocketchip.util.DecoupledHelper
import freechips.rocketchip.rocket.constants.MemoryOpConstants

class rleDecode()(implicit p: Parameters) extends Module
     with MemoryOpConstants {

  val io = IO(new Bundle {
    val l1helperUserRead = new L1MemHelperBundle
    val l1helperUserWrite = new L1MemHelperBundle

    val rle_staged_cmd = Decoupled(new RoCCCommand).flip
    val rle_decode_cmd = Decoupled(new RoCCCommand).flip
    val rocc_out = Decoupled(new RoCCResponse)

  })

val input_length = io.rle_staged_cmd.bits.rs1
val input_pointer = io.rle_staged_cmd.bits.rs2
val output_pointer = io.rle_decode_cmd.bits.rs1
val response_register = io.rle_decode_cmd.bits.inst.rd

// logic here

// create a queue that takes 128 bits and spits out 1 byte
val byte_spitter = Module(new Queue(UInt(8.W), 16)) // 16 entries * 8 bits = 128 bits
val byte_counter = RegInit(0.U(5.W))
val write_queue = Module(new Queue(UInt(8.W), 2))

when (io.rle_staged_cmd.valid) {
  rleLogger.logInfo("input_length: %x\n", input_length)
  rleLogger.logInfo("input_pointer: %x\n", input_pointer)
}

when (io.rle_decode_cmd.valid) {
  rleLogger.logInfo("output_pointer: %x\n", output_pointer)
  rleLogger.logInfo("encoded_length: %x\n", response_register)
}

val request_fire = DecoupledHelper(
    io.l1helperUserRead.req.ready,
    io.rle_staged_cmd.valid,
    io.rle_decode_cmd.valid
)

val response_fire = DecoupledHelper(
  io.l1helperUserWrite.resp.valid, 
  byte_spitter.io.enq.ready
)

val addrinc = RegInit(UInt(0, 64.W))

when (request_fire.fire() && (addrinc === input_length)) {
    addrinc := UInt(0)
} .elsewhen (request_fire.fire()) {
    addrinc := addrinc + UInt(16)
}

io.l1helperUserRead.req.bits.cmd := M_XRD
io.l1helperUserRead.req.bits.size := 4.U
io.l1helperUserRead.req.bits.data := Bits(0)

// instantiate l1helper for write
io.l1helperUserWrite.req.bits.cmd := M_XWR
io.l1helperUserWrite.req.bits.size := 4.U

io.rle_stage_cmd.ready := request_fire.fire(io.rle_stage_cmd.valid) && (addrinc === input_length)
//io.rle_encode_cmd.ready := request_fire.fire(io.rle_encode_cmd.valid) && (addrinc === input_length) move after output is populated

io.l1helperUserRead.req.bits.addr := input_pointer + (addrinc) 
io.l1helperUserRead.req.valid := request_fire.fire(io.l1helperUserRead.req.ready)

// need to pull input_pointer from L2/where it's stored and store in local val
val input_val = io.l1helperUserRead.resp.bits.data // placeholder
byte_spitter.io.enq.valid := response_fire.fire(byte_spitter.io.enq.ready)
io.l1helperUserWrite.resp.ready := response_fire.fire(io.l1helperUserWrite.resp.valid) && (byte_counter === 16.U)

when(byte_spitter.io.enq.valid) {
  // increment the counter
  byte_counter := byte_counter + 1.U

  when (byte_counter === 16.U) {
    // pull in new data from L2
    byte_counter := 0.U
  }
}


byte_spitter.io.enq.bits := (input_val >> (byte_counter << 3.U)) & 0xFF.U

val data_byte = RegInit(0.U(8.W))
val output_byte = RegInit(0.U(8.W))
// create a register that keeps track of the count byte
val count_byte = RegInit(0.U(8.W))
val output_val = RegInit(0.U(128.W))

when(byte_spitter.io.deq.valid) {
    count_byte := byte_spitter.io.deq.bits
    data_byte := byte_spitter.io.deq.bits
}

val i = RegInit(0.U(64.W))
val j = RegInit(count_byte(64.W))
val decoded_length = RegInit(0.U(64.W))

when(i < input_length) {
    output_byte := data_byte
    when (j > 0.U) {
        output_val := Cat(output_byte, data_byte)
        j = j - 1.U
    }

    when(write_queue.io.enq.valid) { // FIXME: cat first
    write_queue.io.enq.bits := output_val
    decoded_length := decoded_length + (PriorityEncoder(Reverse(count)) + 8.U)
    }
    i := i + 1.U
}

when(write_queue.io.deq.valid) { // FIXME: cat first
    cache_val := write_queue.io.deq.bits
  }

// write output val to cache
io.l1helperUserWrite.req.valid := response_fire.fire() //io.l1helperUserWrite.req.valid
io.l1helperUserWrite.req.bits.addr := output_pointer
io.l1helperUserWrite.req.bits.data := cache_val

io.rle_decode_cmd.ready := request_fire.fire(io.rle_decode_cmd.valid) && (addrinc === input_length)

io.rocc_out.bits.rd := response_register
io.rocc_out.bits.data := decoded_length
}




