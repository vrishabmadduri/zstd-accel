package rle

import Chisel._
import chisel3.{Printable}
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig}
import freechips.rocketchip.util.DecoupledHelper
import freechips.rocketchip.rocket.constants.MemoryOpConstants

class rleEncode()(implicit p: Parameters) extends Module
     with MemoryOpConstants {

  val io = IO(new Bundle {
    val l1helperUserRead = new L1MemHelperBundle
    val l1helperUserWrite = new L1MemHelperBundle

    val rle_stage_cmd = Decoupled(new RoCCCommand).flip
    val rle_encode_cmd = Decoupled(new RoCCCommand).flip

  })

val input_length = io.rle_stage_cmd.bits.rs1
val input_pointer = io.rle_stage_cmd.bits.rs2
val output_pointer = io.rle_encode_cmd.bits.rs1
val encoded_length = io.rle_encode_cmd.bits.rs2

// logic here

// create a queue that takes 128 bits and spits out 1 byte
byte_spitter = Module(new Queue(UInt(8.W), 16)) // 16 entries * 8 bits = 128 bits
val byte_counter = RegInit(0.U(5.W))
write_queue = Module(new Queue(UInt(8.W), 2))

when (io.rle_stage_cmd.valid) {
  rleLogger.logInfo("input_length: %x\n", input_length)
  rleLogger.logInfo("input_pointer: %x\n", input_pointer)
}

when (io.rle_encode_cmd.valid) {
  rleLogger.logInfo("output_pointer: %x\n", output_pointer)
  rleLogger.logInfo("encoded_length: %x\n", encoded_length)
}

val request_fire = DecoupledHelper(
    io.l1helperUserRead.req.ready,
    io.rle_stage_cmd.valid, // ???
    io.rle_encode_cmd.valid
)

val response_fire = DecoupledHelper(
  io.l1helperUserWrite.resp.valid, // ???
  byte_spitter.io.enq.ready,
  // counter
)

// counter for address translation/number of requests
// 16-byte address aligned (enforce in software/assert in hardware)

val addrinc = RegInit(UInt(0, 16.W))

when (request_fire.fire() && (addrinc === 16.U)) {
    addrinc := UInt(0)
  } .elsewhen (request_fire.fire()) {
    addrinc := addrinc + UInt(1)
  }

io.l1helperUserRead.req.bits.cmd := M_XRD
io.l1helperUserRead.req.bits.size := 4.U
io.l1helperUserRead.req.bits.data := Bits(0)

// instantiate l1helper for write
io.l1helperUserWrite.req.bits.cmd := M_XWR
io.l1helperUserWrite.req.bits.size := 4.U

io.rle_stage_cmd.ready := request_fire.fire(io.rle_stage_cmd.valid)
io.rle_encode_cmd.ready := request_fire.fire(io.rle_encode_cmd.valid)

io.l1helperUserRead.req.bits.addr := input_pointer + (addrinc) //bitshift addrinc ???
io.l1helperUserRead.req.valid := request_fire.fire(io.l1helperUserRead.req.ready)

// need to pull input_pointer from L2/where it's stored and store in local val
val input_val = io.l1helperUserRead.resp.bits.data // placeholder
byte_spitter.io.enq.valid := request_fire.fire(byte_spitter.io.enq.ready)

when(byte_spitter.io.enq.valid) {
  // increment the counter
  byte_counter := byte_counter + 1
}

if (byte_counter === 16) {
  // pull in new data from L2
  byte_counter := 0
}

byte_spitter.io.enq.bits := input_val(((byte_counter+1)<< 3 - 1), byte_counter << 3)

val current_byte = RegInit(0.U(8.W))
// create a register that keeps track of the previous byte
val previous_byte = RegInit(0.U(8.W))
val output_val = RegInit(0.U(8.W))

when(byte_spitter.io.deq.valid) {
  if (previous_byte === 0.U) {
    previous_byte := byte_spitter.io.deq.bits
  } else {
    previous_byte := current_byte
  }
  current_byte := byte_spitter.io.deq.bits
}

//val start = RegInit(0.U(bit_width.W))
//val end = RegInit(7.U(bit_width.W))
//val encoded_msg = Vec(16, UInt(8.W))) // Bits?
val i = RegInit(0.U(4.W)) // register
val count = RegInit(0.U(4.W))
val j = RegInit(0.U(4.W))
when(i <= input_length - 1) {
  count := 1.U
  ch = previous_byte
  j := i
  when(j < input_length - 1) {
    if (previous_byte === current_byte) {
      count := count + 1.U
      j := j + 1.U // register
      //start := start + 8.U // register
      //end := end + 8.U // register
    }
  }
  // add count, val to Queue
  // then cat count and val at the end and write to memory
  // dequeue every time

  when(write_queue.io.enq.valid) { // FIXME: cat first
    write_queue.io.enq.bits := Cat(count, ch)
  }
//encoded_msg := encoded_msg.fill() populate count and previous_byte every cycle
  i := j + 1.U
}
when(write_queue.io.deq.valid) { // FIXME: cat first
    output_val := write_queue.io.deq.bits
  }

// write output val to cache
io.l1helperUserWrite.req.ready := response_fire.fire(io.l1helperUserWrite.req.valid)
io.l1helperUserWrite.req.bits.addr := output_pointer
io.l1helperUserWrite.req.bits.data := output_val

//encoded_length := output_val.length() ???
}

/*
if (message[j] == message[j+1]):
                count = count+1
                j = j+1
            else:
                break
        encoded_message=encoded_message+str(count)+ch
        i = j+1
    return encoded_message
*/



