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


   


