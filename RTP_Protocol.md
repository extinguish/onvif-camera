# 关于`RTP`协议的`rfc`文档

> 本文是关于`RTP`的[rfc官方文档](https://tools.ietf.org/html/rfc3550)

RTP provides end-to-end network transport functions suitable for
applications transmitting real-time data, such as audio, video or
simulation data, over multicast or unicast network services.  RTP
does not address resource reservation and does not guarantee
quality-of-service for real-time services.  The data transport is
augmented by a control protocol (RTCP) to allow monitoring of the
data delivery in a manner scalable to large multicast networks, and
to provide minimal control and identification functionality.  RTP and
RTCP are designed to be independent of the underlying transport and
network layers.  The protocol supports the use of RTP-level
translators and mixers.

## 1. Introdution

This memorandum specifies the real-time transport protocol (RTP),
   which provides end-to-end delivery services for data with real-time
   characteristics, such as interactive audio and video.  Those services
   include payload type identification, sequence numbering, timestamping
   and delivery monitoring.  Applications typically run RTP on top of
   UDP to make use of its multiplexing and checksum services; both
   protocols contribute parts of the transport protocol functionality.
   However, RTP may be used with other suitable underlying network or
   transport protocols (see Section 11).  RTP supports data transfer to
   multiple destinations using multicast distribution if provided by the
   underlying network.

      Note that RTP itself does not provide any mechanism to ensure timely
   delivery or provide other quality-of-service guarantees, but relies
   on lower-layer services to do so.  It does not guarantee delivery or
   prevent out-of-order delivery, nor does it assume that the underlying
   network is reliable and delivers packets in sequence.  The sequence
   numbers included in RTP allow the receiver to reconstruct the
   sender's packet sequence, but sequence numbers might also be used to
   determine the proper location of a packet, for example in video
   decoding, without necessarily decoding packets in sequence.

     While RTP is primarily designed to satisfy the needs of multi-
   participant multimedia conferences, it is not limited to that
   particular application.  Storage of continuous data, interactive
   distributed simulation, active badge, and control and measurement
   applications may also find RTP applicable.

   This document defines RTP, consisting of two closely-linked parts:

- 1. the real-time transport protocol (RTP), to carry data that has
      real-time properties.

- 2. the RTP control protocol (RTCP), to monitor the quality of service
      and to convey information about the participants in an on-going
      session.  The latter aspect of RTCP may be sufficient for "loosely
      controlled" sessions, i.e., where there is no explicit membership
      control and set-up, but it is not necessarily intended to support
      all of an application's control communication requirements.  This
      functionality may be fully or partially subsumed by a separate
      session control protocol, which is beyond the scope of this
      document.

RTP represents a new style of protocol following the principles of
   application level framing and integrated layer processing proposed by
   Clark and Tennenhouse [10].  That is, RTP is intended to be malleable
   to provide the information required by a particular application and
   will often be integrated into the application processing rather than
   being implemented as a separate layer.  RTP is a protocol framework
   that is deliberately not complete.  This document specifies those
   functions expected to be common across all the applications for which
   RTP would be appropriate.  Unlike conventional protocols in which
   additional functions might be accommodated by making the protocol
   more general or by adding an option mechanism that would require
   parsing, RTP is intended to be tailored through modifications and/or
   additions to the headers as needed.  Examples are given in Sections
   5.3 and 6.4.3.

   Therefore, in addition to this document, a complete specification of
   RTP for a particular application will require one or more companion
   documents (see Section 13):

   o  a profile specification document, which defines a set of payload
      type codes and their mapping to payload formats (e.g., media
      encodings).  A profile may also define extensions or modifications
      to RTP that are specific to a particular class of applications.
      Typically an application will operate under only one profile.  A
      profile for audio and video data may be found in the companion RFC
      3551 [1].

   o  payload format specification documents, which define how a
      particular payload, such as an audio or video encoding, is to be
      carried in RTP.

   A discussion of real-time services and algorithms for their
   implementation as well as background discussion on some of the RTP
   design decisions can be found in [11].





## 4. Byte Order, Alignment, and Time Format

All integer fields are carried in network byte order, that is, most
significant byte (octet) first.  This byte order is commonly known as
big-endian.  The transmission order is described in detail in [3].
Unless otherwise noted, numeric constants are in decimal (base 10).

All header data is aligned to its natural length, i.e., 16-bit fields
are aligned on even offsets, 32-bit fields are aligned at offsets
divisible by four, etc.  Octets designated as padding have the value
zero.

Wallclock time (absolute date and time) is represented using the
timestamp format of the Network Time Protocol (NTP), which is in
seconds relative to 0h UTC on 1 January 1900 [4].  The full
resolution NTP timestamp is a 64-bit unsigned fixed-point number with
the integer part in the first 32 bits and the fractional part in the
last 32 bits.  In some fields where a more compact representation is
appropriate, only the middle 32 bits are used; that is, the low 16
bits of the integer part and the high 16 bits of the fractional part.
The high 16 bits of the integer part must be determined
independently.

An implementation is not required to run the Network Time Protocol in
   order to use RTP.  Other time sources, or none at all, may be used
   (see the description of the NTP timestamp field in Section 6.4.1).
   However, running NTP may be useful for synchronizing streams
   transmitted from separate hosts.

   The NTP timestamp will wrap around to zero some time in the year
   2036, but for RTP purposes, only differences between pairs of NTP
   timestamps are used.  So long as the pairs of timestamps can be
   assumed to be within 68 years of each other, using modular arithmetic
   for subtractions and comparisons makes the wraparound irrelevant.


## 5. RTP Data Transfer Protocol

### 5.1 RTP Data Transfer Protocol

The RTP header has the following format:

```
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P|X|  CC   |M|     PT      |       sequence number         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                           timestamp                           |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |           synchronization source (SSRC) identifier            |
   +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
   |            contributing source (CSRC) identifiers             |
   |                             ....                              |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

The first twelve octets are present in `every` RTP packet, while the
list of `CSRC` identifiers is present only when inserted by a mixer.
The fields have the following meaning:

- version (V): 2 bits
    This field identifies the version of RTP.  The version defined by
    this specification is two (2).  (The value 1 is used by the first
    draft version of RTP and the value 0 is used by the protocol
    initially implemented in the "vat" audio tool.)

- padding (P): 1 bit
    If the padding bit is set, the packet contains one or more
    additional padding octets at the end which are not part of the
    payload.  The last octet of the padding contains a count of how
    many padding octets should be ignored, including itself.  Padding
    may be needed by some encryption algorithms with fixed block sizes
    or for carrying several RTP packets in a lower-layer protocol data
    unit.

- extension (X): 1 bit
    If the extension bit is set, the fixed header MUST be followed by
    exactly one header extension, with a format defined in Section
    5.3.1.

- `CSRC count (CC)`: 4 bits
    The CSRC count contains the number of `CSRC identifiers` that follow
    the fixed header.

- marker (M): 1 bit
    The interpretation of the marker is defined by a profile.  It is
    intended to allow significant events such as `frame boundaries` to
    be marked in the packet stream.  A profile MAY define additional
    marker bits or specify that there is no marker bit by changing the
    number of bits in the payload type field (see Section 5.3).

- payload type (PT): 7 bits
    This field identifies the format of the `RTP payload` and determines
    its interpretation by the application.  A profile MAY specify a
    default static mapping of `payload type codes` to `payload formats`.
    Additional payload type codes MAY be defined dynamically through
    `non-RTP means` (see Section 3).  A set of default mappings for
    audio and video is specified in the companion RFC 3551 [1].  An
    RTP source MAY `change` the payload type during a session, but this
    field `SHOULD NOT` be used for `multiplexing` separate media streams
    (see Section 5.2).

    A receiver `MUST ignore` packets with payload types that it does not
    understand.

- sequence number: 16 bits
    The sequence number increments by `one` for each RTP data packet
    sent, and may be used by the receiver to `detect packet loss` and to
    `restore packet sequence`.  The initial value of the sequence number
    `SHOULD` be `random` (unpredictable) to make known-plaintext attacks
    on encryption more difficult, even if the source itself does not
    encrypt according to the method in Section 9.1, because the
    packets may flow through a translator that does.  Techniques for
    choosing unpredictable numbers are discussed in [17].

- timestamp: 32 bits
    The timestamp reflects the `sampling instant(即时采样)` of the `first octet(第一个八位字节)` in
    the RTP data packet.  The `sampling instant` MUST be derived from a
    clock that increments `monotonically and linearly(单调和线性)` in time to allow
    synchronization and `jitter calculations(抖动计算)` (see Section 6.4.1).  The
    resolution of the clock MUST be sufficient for the desired
    synchronization accuracy and for measuring packet arrival jitter
    (one tick per video frame is typically `not sufficient`).  The clock
    frequency is dependent on the format of data carried as payload
    and is specified statically in the profile or payload format
    specification that defines the format, or MAY be specified
    dynamically for payload formats defined through non-RTP means.  If
    RTP packets are generated periodically, the nominal sampling
    instant as determined from the sampling clock is to be used, not a
    reading of the system clock.  As an example, for fixed-rate audio
    the timestamp clock would likely increment by one for each
    sampling period.  If an audio application reads blocks covering
    160 sampling periods from the input device, the timestamp would be
    increased by 160 for each such block, regardless of whether the
    block is transmitted in a packet or dropped as silent.

    The initial value of the timestamp SHOULD be random, as for the
    sequence number.  Several consecutive RTP packets will have equal
    timestamps if they are (logically) generated at once, e.g., belong
    to the same video frame.  Consecutive RTP packets MAY contain
    timestamps that are not monotonic if the data is not transmitted
    in the order it was sampled, as in the case of MPEG interpolated
    video frames.  (The sequence numbers of the packets as transmitted
    will still be monotonic.)

    RTP timestamps from different media streams may advance at
    different rates and usually have independent, random offsets.
    Therefore, although these timestamps are sufficient to reconstruct
    the timing of a single stream, directly comparing RTP timestamps
    from different media is not effective for synchronization.
      Instead, for each medium the RTP timestamp is related to the
      sampling instant by pairing it with a timestamp from a reference
      clock (wallclock) that represents the time when the data
      corresponding to the RTP timestamp was sampled.  The reference
      clock is shared by all media to be synchronized.  The timestamp
      pairs are not transmitted in every data packet, but at a lower
      rate in RTCP SR packets as described in Section 6.4.

      The sampling instant is chosen as the point of reference for the
      RTP timestamp because it is known to the transmitting endpoint and
      has a common definition for all media, independent of encoding
      delays or other processing.  The purpose is to allow synchronized
      presentation of all media sampled at the same time.

      Applications transmitting stored data rather than data sampled in
      real time typically use a virtual presentation timeline derived
      from wallclock time to determine when the next frame or other unit
      of each medium in the stored data should be presented.  In this
      case, the RTP timestamp would reflect the presentation time for
      each unit.  That is, the RTP timestamp for each unit would be
      related to the wallclock time at which the unit becomes current on
      the virtual presentation timeline.  Actual presentation occurs
      some time later as determined by the receiver.

      An example describing live audio narration of prerecorded video
      illustrates the significance of choosing the sampling instant as
      the reference point.  In this scenario, the video would be
      presented locally for the narrator to view and would be
      simultaneously transmitted using RTP.  The "sampling instant" of a
      video frame transmitted in RTP would be established by referencing
    its timestamp to the wallclock time when that video frame was
    presented to the narrator.  The sampling instant for the audio RTP
    packets containing the narrator's speech would be established by
    referencing the same wallclock time when the audio was sampled.
    The audio and video may even be transmitted by different hosts if
    the reference clocks on the two hosts are synchronized by some
    means such as NTP.  A receiver can then synchronize presentation
    of the audio and video packets by relating their RTP timestamps
    using the timestamp pairs in RTCP SR packets.






----------------------------------------------------------------

## [关于可以通过`rtp`进行传输视频流的各种方式](https://cardinalpeak.com/blog/the-many-ways-to-stream-video-using-rtp-and-rtsp/)


Something I end up explaining relatively often has to do with all the various ways you can stream video encapsulated in the `Real-time Transport Protocol, or RTP`, and still claim to be standards compliant.

Some background: `RTP` is used primarily to stream either `H.264` or `MPEG-4` video. `RTP` is a system protocol that provides mechanisms to synchronize the presentation different streams – for instance audio and video. As such, it performs some of the same functions as an `MPEG-2` transport or program stream.

`RTP` – which you can read about in great detail in [RFC 3550](http://www.rfc-editor.org/rfc/rfc3550.pdf) – is `codec-agnostic(编解码器无关)`. This means it is possible to carry a large number of `codec types` inside RTP; for `each protocol`, the `IETF` defines an `RTP profile` that specifies any `codec-specific` details of mapping data from the `codec` into RTP `packets`. Profiles are defined for `H.264`, `MPEG-4` video and audio, and many more. Even `VC-1` – the “standardized” form of `Windows Media Video` – has an `RTP profile`.

In my opinion, the standards are a mess in this area. It should be possible to meet all the various requirements on streaming video with one or at most two different methods for streaming. But ultimately standards bodies are committees: each person puts in a pretty color, and the result comes out grey.

In fact, the original standards situation around MPEG-4 video was so confused that a group of large companies formed the Internet Streaming Media Alliance, or ISMA. ISMA’s role is basically to wade into all the different options presented in the standards and create a meta-standard – currently ISMA 2.0 – that ties a number of other standards documents together and tells you how to build a working system that will interoperate with other systems.

In any event, there are a number of predominant ways to send MPEG-4 or H.264 video using RTP, all of which follow some relevant standards. If you’re writing a decoder, you’ll normally need to address all of them, so here’s a quick overview.

### Multicast delivery: `RTP over UDP`

In an environment where there is `one source` of a video stream and `many viewers`, ideally each frame of video and audio would only transit the network `once`.  This is how `multicast` delivery works. In a multicast network, each viewer must `retrieve` an `SDP file` through some `unspecified mechanism`, which in practice is usually `HTTP`. Once retrieved, the SDP file gives enough information for the viewer to `find` the `multicast streams` on the network and begin `playback`.

In the `Multicast delivery scenario`, each individual stream is sent on `a pair` of different UDP ports – one for `data` and the second for the `related RTP Control Protocol` or _RTCP_. That means for a video program consisting of a video stream and two audio streams, you’ll actually see packets being delivered to `six UDP ports`:

- 1. Video data delivered over RTP
- 2. The related RTCP port for the video stream
- 3. Primary audio data delivered over RTP
- 4. The related RTCP port for the primary audio stream
- 5. Secondary audio data delivered over RTP
- 6. The related RTCP port for the secondary audio stream

`Timestamps` in the RTP headers can be used to `synchronize` the presentation of the various streams.

As a side note, `RTCP` is almost _vestigial(痕迹)_ for most applications. It’s specified in [RFC 3550](http://www.rfc-editor.org/rfc/rfc3550.pdf) along with `RTP`.  If you’re implementing a `decoder` you’ll need to listen on the `RTCP ports`, but you can almost `ignore` any data sent to you. The exceptions are the `sender report`, which you’ll need in order to match up the timestamps between the streams, and the `BYE`, which some sources will send as they `tear down` a stream.

Multicast video delivery works best for `live content`. Because each viewer is viewing the same stream, it’s **not possible** for individual viewers to be able to pause, seek, rewind or fast-forward the stream.

### Unicast delivery: `RTP over UDP`

It’s also possible to send `unicast` video over `UDP`, with one copy of the video transiting the network for each client. `Unicast delivery` can be used for both `live` and `stored content`. In the `stored content` case, additional control commands can be used to pause, seek, and enter fast forward and rewind modes.

Normally in this case, the player first establishes a `control connection` to a server using the `Real Time Streaming Protocol`, or RTSP. In theory RTSP can be used over either UDP or TCP, but in practice it is almost `always` used over `TCP`.

The player is normally started with an `rtsp://` URL, and this causes it to connect over `TCP` to the `RTSP server`. After some back-and-forth between the player and the RTSP server, during which the server sends the client an SDP file describing the stream, the server begins sending video to the client over `UDP`. As with the multicast delivery case, a pair of UDP ports is used for each of the `elementary streams`.

For `seekable streams`, once the video is playing, the player has `additional control` using `RTSP`: It can cause playback to `pause`, or `seek` to a different position, or enter fast forward or rewind mode.

### RTSP Interleaved mode: `RTP and RTSP over TCP`

I’m not a fan of streaming video over TCP. In the event a packet is lost in the network, it’s usually worse to wait for a retransmission (which is what happens with TCP’s guaranteed delivery) than it is just to allow the resulting video `glitch` to pass through to the user (which is what happens with UDP).

However, there are a handful of different networking configurations that would block UDP video; in particular, firewalls historically have interacted badly with the two modes of UDP delivery summarized above.

So the [RTSP RFC](http://www.rfc-editor.org/rfc/rfc2326.txt), in section 10.12, briefly outlines a mode of `interleaving` the `RTP and RTCP` packets onto the **existing** `TCP` connection being used for `RTSP`. Each `RTP and RTCP` packet is given a `four-byte` prefix and dropped onto the TCP stream. The result is that the player connects to the RTSP server, and all communication flows over a single TCP connection between the two.

### HTTP Tunneled mode: `RTP and RTSP over HTTP over TCP`

You would think RTSP Interleaved mode, being designed to transmit video across firewalls, would be the end, but it turns out that many firewalls aren’t configured to allow connections to TCP port 554, the well-known port for an RTSP server.

So **Apple** invented a method of `mapping` the entire RTSP Interleaved communication on top of HTTP, meaning the video ultimately flows across TCP port 80. To my knowledge, this `HTTP Tunneled mode` is **not standardized** in any official RFC, but it’s so widely implemented that it has become a de-facto standard.

-----------------------------------------------------------------

以下是关于`RTSP`当中的`inerleaving`模式(来自于`rtsp`的[rfc文档-10.12节](http://www.rfc-editor.org/rfc/rfc2326.txt))

### Embedded (Interleaved) Binary Data

Certain firewall designs and other circumstances may force a server
to interleave RTSP methods and stream data. This interleaving should
generally be avoided unless necessary since it complicates client and
server operation and imposes additional overhead. Interleaved binary
data **SHOULD** only be used if RTSP is carried over `TCP`.

Stream data such as `RTP packets` is encapsulated by an `ASCII dollar`
sign (`24 hexadecimal`), followed by a `one-byte` **channel identifier**,
followed by the length of the encapsulated binary data as a binary,
`two-byte integer` in network byte order. The stream data follows
immediately afterwards, **without** a `CRLF`, but including the upper-layer
protocol headers. Each `$` block contains exactly one upper-layer
protocol data unit, e.g., one RTP packet.

The `channel identifier` is defined in the Transport header with the
interleaved parameter(Section 12.39).

When the transport choice is RTP, RTCP messages are also interleaved
by the server over the `TCP connection`. As a default, `RTCP` packets are
sent on the first available channel higher than the RTP channel. The
client MAY explicitly request RTCP packets on another channel. This
is done by specifying two channels in the interleaved parameter of
the `Transport header`(Section 12.39).

`RTCP` is needed for synchronization when two or more streams are
interleaved in such a fashion. Also, this provides a convenient way
to tunnel `RTP/RTCP` packets through the TCP control connection when
required by the network configuration and transfer them onto UDP
when possible.

```
     C->S: SETUP rtsp://foo.com/bar.file RTSP/1.0
           CSeq: 2
           Transport: RTP/AVP/TCP;interleaved=0-1

     S->C: RTSP/1.0 200 OK
           CSeq: 2
           Date: 05 Jun 1997 18:57:18 GMT
           Transport: RTP/AVP/TCP;interleaved=0-1

           Session: 12345678

     C->S: PLAY rtsp://foo.com/bar.file RTSP/1.0
           CSeq: 3
           Session: 12345678

     S->C: RTSP/1.0 200 OK
           CSeq: 3
           Session: 12345678
           Date: 05 Jun 1997 18:59:15 GMT
           RTP-Info: url=rtsp://foo.com/bar.file;
             seq=232433;rtptime=972948234

     S->C: $\000{2 byte length}{"length" bytes data, w/RTP header}
     S->C: $\000{2 byte length}{"length" bytes data, w/RTP header}
     S->C: $\001{2 byte length}{"length" bytes  RTCP packet}
```

















