package spatial.tests.apps

import spatial.dsl._


@spatial object SHA_PREPROCESSING extends SpatialApp {

  type ULong = FixPt[FALSE, _32, _0]
  type UInt8 = FixPt[FALSE, _8, _0]

  def main(args: Array[String]): Unit = {
    // Setup off-chip data
    val raw_text = args(0)
    val data_text_int = raw_text.map[U8]{c => c}
    val data_text = Array.tabulate(data_text_int.length){i => data_text_int(i).to[UInt8]}
    val len = HostIO[Int]
    setArg(len, data_text.length)
    val text_dram = DRAM[UInt8](1024)
    val hash_dram = DRAM[UInt8](32)//(5)

    println("Hashing: " + raw_text + " (len: " + data_text.length + ")")
    setMem(text_dram, data_text)

    Accel{
      // Init
      val datalen = Reg[Int](0)
      val bitlen = RegFile[ULong](2, List(0.to[ULong],0.to[ULong]))
      val NUM_CHUNKS = 8
      val data = SRAM[UInt8](NUM_CHUNKS * 64)
      val m_preprocess = SRAM[ULong](NUM_CHUNKS, 64)

      // Initial hash values that will be put in h0-h7
      val state = RegFile[ULong](8, List(0x6a09e667L.to[ULong],0xbb67ae85L.to[ULong],0x3c6ef372L.to[ULong],0xa54ff53aL.to[ULong],
        0x510e527fL.to[ULong],0x9b05688cL.to[ULong],0x1f83d9abL.to[ULong],0x5be0cd19L.to[ULong])
      )
      val hash = SRAM[UInt8](32)
      val K_LUT = LUT[ULong](64)(
        0x428a2f98L.to[ULong],0x71374491L.to[ULong],0xb5c0fbcfL.to[ULong],0xe9b5dba5L.to[ULong],0x3956c25bL.to[ULong],0x59f111f1L.to[ULong],0x923f82a4L.to[ULong],0xab1c5ed5L.to[ULong],
        0xd807aa98L.to[ULong],0x12835b01L.to[ULong],0x243185beL.to[ULong],0x550c7dc3L.to[ULong],0x72be5d74L.to[ULong],0x80deb1feL.to[ULong],0x9bdc06a7L.to[ULong],0xc19bf174L.to[ULong],
        0xe49b69c1L.to[ULong],0xefbe4786L.to[ULong],0x0fc19dc6L.to[ULong],0x240ca1ccL.to[ULong],0x2de92c6fL.to[ULong],0x4a7484aaL.to[ULong],0x5cb0a9dcL.to[ULong],0x76f988daL.to[ULong],
        0x983e5152L.to[ULong],0xa831c66dL.to[ULong],0xb00327c8L.to[ULong],0xbf597fc7L.to[ULong],0xc6e00bf3L.to[ULong],0xd5a79147L.to[ULong],0x06ca6351L.to[ULong],0x14292967L.to[ULong],
        0x27b70a85L.to[ULong],0x2e1b2138L.to[ULong],0x4d2c6dfcL.to[ULong],0x53380d13L.to[ULong],0x650a7354L.to[ULong],0x766a0abbL.to[ULong],0x81c2c92eL.to[ULong],0x92722c85L.to[ULong],
        0xa2bfe8a1L.to[ULong],0xa81a664bL.to[ULong],0xc24b8b70L.to[ULong],0xc76c51a3L.to[ULong],0xd192e819L.to[ULong],0xd6990624L.to[ULong],0xf40e3585L.to[ULong],0x106aa070L.to[ULong],
        0x19a4c116L.to[ULong],0x1e376c08L.to[ULong],0x2748774cL.to[ULong],0x34b0bcb5L.to[ULong],0x391c0cb3L.to[ULong],0x4ed8aa4aL.to[ULong],0x5b9cca4fL.to[ULong],0x682e6ff3L.to[ULong],
        0x748f82eeL.to[ULong],0x78a5636fL.to[ULong],0x84c87814L.to[ULong],0x8cc70208L.to[ULong],0x90befffaL.to[ULong],0xa4506cebL.to[ULong],0xbef9a3f7L.to[ULong],0xc67178f2L.to[ULong]
      )

      def SHFR(x: ULong, y: Int): ULong = {
        val tmp = Reg[ULong](0)
        tmp := x
        Foreach(y by 1){_ => tmp := tmp >> 1}
        tmp.value
      }

      // DBL_INT_ADD treats two unsigned ints a and b as one 64-bit integer and adds c to it
      def DBL_INT_ADD(c:ULong): Unit = {
        if (bitlen(0) > 0xffffffffL.to[ULong] - c) {bitlen(1) = bitlen(1) + 1}
        bitlen(0) = bitlen(0) + c
      }

      def SIG0(x:ULong): ULong = {
        // (ROTRIGHT(x,7) ^ ROTRIGHT(x,18) ^ ((x) >> 3))
        ( x >> 7 | x << (32-7) ) ^ ( x >> 18 | x << (32-18) ) ^ x >> 3
      }

      def SIG1(x:ULong): ULong = {
        // (ROTRIGHT(x,17) ^ ROTRIGHT(x,19) ^ ((x) >> 10))
        ( x >> 17 | x << (32-17) ) ^ ( x >> 19 | x << (32-19) ) ^ x >> 10
      }

      def CH(x:ULong, y:ULong, z:ULong): ULong = {
        // (((x) & (y)) ^ (~(x) & (z)))
        (x & y) ^ (~x & z)
      }

      def MAJ(x:ULong, y:ULong, z:ULong): ULong = {
        // (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
        (x & y) ^ (x & z) ^ (y & z)
      }

      def EP0(x: ULong): ULong = {
        // (ROTRIGHT(x,2) ^ ROTRIGHT(x,13) ^ ROTRIGHT(x,22))
        ( x >> 2 | x << (32-2) ) ^ ( x >> 13 | x << (32-13) ) ^ ( x >> 22 | x << (32-22) )
      }

      def EP1(x: ULong): ULong = {
        // (ROTRIGHT(x,6) ^ ROTRIGHT(x,11) ^ ROTRIGHT(x,25))
        ( x >> 6 | x << (32-6) ) ^ ( x >> 11 | x << (32-11) ) ^ ( x >> 25 | x << (32-25) )
      }

      def sha_transform_preprocess(chunk_idx: I32): Unit = {
        val A = Reg[ULong]
        val B = Reg[ULong]
        val C = Reg[ULong]
        val D = Reg[ULong]
        val E = Reg[ULong]
        val F = Reg[ULong]
        val G = Reg[ULong]
        val H = Reg[ULong]

        A := state(0)
        B := state(1)
        C := state(2)
        D := state(3)
        E := state(4)
        F := state(5)
        G := state(6)
        H := state(7)
        

        Foreach(64 by 1){ i =>
          val tmp1 = H + EP1(E) + CH(E,F,G) + K_LUT(i) + m_preprocess(chunk_idx, i)
          val tmp2 = EP0(A) + MAJ(A,B,C)
          // println(" " + i + " : " + A.value + " " + B.value + " " +
          //   C.value + " " + D.value + " " + E.value + " " + F.value + " " + G.value + " " + H.value)
          // println("    " + H.value + " " + EP1(E) + " " + CH(E,F,G) + " " + K_LUT(i) + " " + m(i))
          H := G; G := F; F := E; E := D + tmp1; D := C; C := B; B := A; A := tmp1 + tmp2
        }

        Foreach(8 by 1 par 8){i =>
          state(i) = state(i) + mux(i == 0, A, mux(i == 1, B, mux(i == 2, C, mux(i == 3, D,
            mux(i == 4, E, mux(i == 5, F, mux(i == 6, G, H)))))))
        }

      }

      // Populates a chunk of the m array (up to 512 bits * NUM_CHUNKS)
      def populate_m_big_chunk(num_chunks: Int): Unit = {
        Foreach(0 until num_chunks.as[I32] by 1 par NUM_CHUNKS) { chunk_idx =>
          Sequential.Foreach(0 until 64 by 1){ byte_idx =>
            if ( byte_idx < 16 ) {
              val j = byte_idx << 2
              // println(" m_preprocess(" + chunk_idx + ", " + byte_idx + ") = " + {(data(chunk_idx*64 + j).as[ULong] << 24) | (data(chunk_idx*64 + j+1).as[ULong] << 16) | (data(chunk_idx*64 + j+2).as[ULong] << 8) | (data(chunk_idx*64 + j+3).as[ULong])})
              m_preprocess(chunk_idx, byte_idx) = (data(chunk_idx*64 + j).as[ULong] << 24) | (data(chunk_idx*64 + j+1).as[ULong] << 16) | (data(chunk_idx*64 + j+2).as[ULong] << 8) | (data(chunk_idx*64 + j+3).as[ULong])
            } else {
              // println(" m_preprocess(" + chunk_idx + ", " + byte_idx + ") = " + SIG1(m_preprocess(chunk_idx, byte_idx-2)) + " " + m_preprocess(chunk_idx, byte_idx-7) + " " + SIG0(m_preprocess(chunk_idx, byte_idx-15)) + " " + m_preprocess(chunk_idx, byte_idx-16))
              m_preprocess(chunk_idx, byte_idx) = SIG1(m_preprocess(chunk_idx, byte_idx-2)) + m_preprocess(chunk_idx, byte_idx-7) + SIG0(m_preprocess(chunk_idx, byte_idx-15)) + m_preprocess(chunk_idx, byte_idx-16)
            }
          }
        }
      }

      // Populates a chunk of the m array (up to 512 bits)
      def populate_m_chunk(chunk_idx: I32): Unit = {
        Sequential.Foreach(0 until 64 by 1){ byte_idx =>
          if ( byte_idx < 16 ) {
            val j = byte_idx << 2
            // println(" m_preprocess(" + chunk_idx + ", " + byte_idx + ") = " + {(data(chunk_idx*64 + j).as[ULong] << 24) | (data(chunk_idx*64 + j+1).as[ULong] << 16) | (data(chunk_idx*64 + j+2).as[ULong] << 8) | (data(chunk_idx*64 + j+3).as[ULong])})
            m_preprocess(chunk_idx, byte_idx) = (data(chunk_idx*64 + j).as[ULong] << 24) | (data(chunk_idx*64 + j+1).as[ULong] << 16) | (data(chunk_idx*64 + j+2).as[ULong] << 8) | (data(chunk_idx*64 + j+3).as[ULong])
          } else {
            // println(" m_preprocess(" + chunk_idx + ", " + byte_idx + ") = " + SIG1(m_preprocess(chunk_idx, byte_idx-2)) + " " + m_preprocess(chunk_idx, byte_idx-7) + " " + SIG0(m_preprocess(chunk_idx, byte_idx-15)) + " " + m_preprocess(chunk_idx, byte_idx-16))
            m_preprocess(chunk_idx, byte_idx) = SIG1(m_preprocess(chunk_idx, byte_idx-2)) + m_preprocess(chunk_idx, byte_idx-7) + SIG0(m_preprocess(chunk_idx, byte_idx-15)) + m_preprocess(chunk_idx, byte_idx-16)
          }
        }
      }

      def SHA256(): Unit = {
        // PREPROCESSING
        Sequential.Foreach(0 until len.value by 64*NUM_CHUNKS) { base =>
          val data_chunk_size = min(floor((len.value-base)/64)*64, 64*NUM_CHUNKS)
          data(0::data_chunk_size) load text_dram(base::base+data_chunk_size)
          populate_m_big_chunk((data_chunk_size/64).as[Int])

          // HASHING
          Sequential.Foreach(0 until 64*NUM_CHUNKS by 64 par 1) { i =>
            val byte_idx = base + i
            if (byte_idx <= len.value) {
              val chunk_idx_transform = i/64
              datalen := min(len.value - byte_idx, 64)

              if (datalen.value == 64.to[Int]) {
                sha_transform_preprocess(chunk_idx_transform)
                DBL_INT_ADD(512);
              }
            }
          }
        }
        
        // Process the final chunk if the message wasn't divisible into 512-bit chunks
        val end_chunk_byte_start = floor(len.value / 64) * 64
        val chunk_idx = Reg[Int](0)
        datalen := len.value - end_chunk_byte_start
        data(chunk_idx::chunk_idx+datalen.value) load text_dram(end_chunk_byte_start::end_chunk_byte_start+datalen.value)
        val pad_stop = if (datalen.value < 56) 56 else 64
        Foreach(datalen until pad_stop by 1){i => data(chunk_idx*64 + i) = if (i == datalen.value) 0x80.to[UInt8] else 0.to[UInt8]}
        if (datalen.value >= 56) {
          populate_m_chunk(chunk_idx)
          sha_transform_preprocess(chunk_idx)
          Foreach(56 by 1){i => data(i) = 0}
        }

        DBL_INT_ADD(datalen.value.to[ULong] * 8.to[ULong])
        Pipe{data(chunk_idx*64 + 63) = (bitlen(0)).to[UInt8]}
        Pipe{data(chunk_idx*64 + 62) = (bitlen(0) >> 8).to[UInt8]}
        Pipe{data(chunk_idx*64 + 61) = (bitlen(0) >> 16).to[UInt8]}
        Pipe{data(chunk_idx*64 + 60) = (bitlen(0) >> 24).to[UInt8]}
        Pipe{data(chunk_idx*64 + 59) = (bitlen(1)).to[UInt8]}
        Pipe{data(chunk_idx*64 + 58) = (bitlen(1) >> 8).to[UInt8]}
        Pipe{data(chunk_idx*64 + 57) = (bitlen(1) >> 16).to[UInt8]}
        Pipe{data(chunk_idx*64 + 56) = (bitlen(1) >> 24).to[UInt8]}
        
        populate_m_chunk(chunk_idx)
        sha_transform_preprocess(chunk_idx)

        Sequential.Foreach(4 by 1){ i =>
          hash(i)    = (SHFR(state(0), (24-i*8))).bits(7::0).as[UInt8]
          hash(i+4)  = (SHFR(state(1), (24-i*8))).bits(7::0).as[UInt8]
          hash(i+8)  = (SHFR(state(2), (24-i*8))).bits(7::0).as[UInt8]
          hash(i+12) = (SHFR(state(3), (24-i*8))).bits(7::0).as[UInt8]
          hash(i+16) = (SHFR(state(4), (24-i*8))).bits(7::0).as[UInt8]
          hash(i+20) = (SHFR(state(5), (24-i*8))).bits(7::0).as[UInt8]
          hash(i+24) = (SHFR(state(6), (24-i*8))).bits(7::0).as[UInt8]
          hash(i+28) = (SHFR(state(7), (24-i*8))).bits(7::0).as[UInt8]
        }
      }

      SHA256()
      hash_dram store hash
    }

    val hashed_result = getMem(hash_dram)
    // Input for this hash: 0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695
    // val hashed_gold = Array[UInt8](56.to[UInt8], 241.to[UInt8], 171.to[UInt8], 222.to[UInt8], 203.to[UInt8], 126.to[UInt8], 0.to[UInt8], 26.to[UInt8], 180.to[UInt8], 3.to[UInt8], 174.to[UInt8], 205.to[UInt8], 136.to[UInt8], 90.to[UInt8], 1.to[UInt8], 13.to[UInt8], 90.to[UInt8], 37.to[UInt8], 64.to[UInt8], 84.to[UInt8], 228.to[UInt8], 121.to[UInt8], 211.to[UInt8], 53.to[UInt8], 39.to[UInt8], 62.to[UInt8], 170.to[UInt8], 28.to[UInt8], 189.to[UInt8], 214.to[UInt8], 38.to[UInt8], 116.to[UInt8])
    // Input for this hash: 0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695
    // val hashed_gold = Array[UInt8](39.to[UInt8], 179.to[UInt8], 231.to[UInt8], 53.to[UInt8], 65.to[UInt8], 34.to[UInt8], 88.to[UInt8], 63.to[UInt8], 235.to[UInt8], 39.to[UInt8], 249.to[UInt8], 180.to[UInt8], 234.to[UInt8], 174.to[UInt8], 48.to[UInt8], 12.to[UInt8], 240.to[UInt8], 202.to[UInt8], 142.to[UInt8], 175.to[UInt8], 196.to[UInt8], 69.to[UInt8], 89.to[UInt8], 1.to[UInt8], 62.to[UInt8], 189.to[UInt8], 72.to[UInt8], 187.to[UInt8], 229.to[UInt8], 211.to[UInt8], 251.to[UInt8], 8.to[UInt8])
    // input = "0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a1"
    val hashed_gold = Array[UInt8](148.to[UInt8], 93.to[UInt8], 186.to[UInt8], 114.to[UInt8], 177.to[UInt8], 232.to[UInt8], 120.to[UInt8], 47.to[UInt8], 124.to[UInt8], 31.to[UInt8], 183.to[UInt8], 235.to[UInt8], 146.to[UInt8], 229.to[UInt8], 123.to[UInt8], 17.to[UInt8], 101.to[UInt8], 125.to[UInt8], 120.to[UInt8], 255.to[UInt8], 103.to[UInt8], 220.to[UInt8], 141.to[UInt8], 14.to[UInt8], 89.to[UInt8], 46.to[UInt8], 1.to[UInt8], 145.to[UInt8], 99.to[UInt8], 181.to[UInt8], 252.to[UInt8], 84.to[UInt8])
    // Input for this hash: 0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695
    // val hashed_gold = Array[UInt8](84.to[UInt8], 250.to[UInt8], 27.to[UInt8], 231.to[UInt8], 95.to[UInt8], 140.to[UInt8], 37.to[UInt8], 74.to[UInt8], 103.to[UInt8], 180.to[UInt8], 219.to[UInt8], 94.to[UInt8], 253.to[UInt8], 40.to[UInt8], 24.to[UInt8], 247.to[UInt8], 71.to[UInt8], 86.to[UInt8], 29.to[UInt8], 227.to[UInt8], 36.to[UInt8], 227.to[UInt8], 72.to[UInt8], 30.to[UInt8], 80.to[UInt8], 163.to[UInt8], 163.to[UInt8], 79.to[UInt8], 207.to[UInt8], 50.to[UInt8], 180.to[UInt8], 239.to[UInt8])
    // Input for this hash: 0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a146950100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695
    // val hashed_gold = Array[UInt8](21.to[UInt8], 34.to[UInt8], 64.to[UInt8], 225.to[UInt8], 29.to[UInt8], 149.to[UInt8], 163.to[UInt8], 6.to[UInt8], 143.to[UInt8], 217.to[UInt8], 7.to[UInt8], 236.to[UInt8], 19.to[UInt8], 17.to[UInt8], 171.to[UInt8], 62.to[UInt8], 243.to[UInt8], 148.to[UInt8], 9.to[UInt8], 253.to[UInt8], 87.to[UInt8], 144.to[UInt8], 118.to[UInt8], 209.to[UInt8], 136.to[UInt8], 242.to[UInt8], 143.to[UInt8], 73.to[UInt8], 153.to[UInt8], 133.to[UInt8], 230.to[UInt8], 82.to[UInt8])
    printArray(hashed_gold, "Expected: ")
    printArray(hashed_result, "Got: ")

    val cksum = hashed_gold.zip(hashed_result){_==_}.reduce{_&&_}
    println("PASS: " + cksum + " (SHA PREPROCESS)")
    assert(cksum)
  }
}
