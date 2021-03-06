package xmlrpc

import java.util.Date

import org.scalatest.FunSpec
import xmlrpc.protocol.Deserializer.Fault
import xmlrpc.protocol.{Datatype, XmlrpcProtocol}

import scala.xml.NodeSeq

class XmlrpcSpec extends FunSpec {
  import XmlrpcProtocol._
  import org.scalatest.StreamlinedXmlEquality._

  def assertCanBeSerializedAndDeserialized[T: Datatype](value: T): Unit = {
    assertResult(value) {
      readXmlResponse[T](writeXmlRequest[T]("testMethod", value).asResponse).toOption.get
    }
  }

  def assertResponseIsRead[T: Datatype](expected: T, xml: NodeSeq): Unit = {
    assertResult(expected) {
      readXmlResponse[T](xml).toOption.get
    }
  }

  describe("Xmlrpc protocol") {
    it("should create a right xmlrpc request") {
      val request: NodeSeq =
        <methodCall>
          <methodName>{"examples.getStateName"}</methodName>
          <params>
            <param>
              <value><int>{41}</int></value>
            </param>
          </params>
        </methodCall>

      assert(
        request ===
          writeXmlRequest[Int]("examples.getStateName", 41)
      )
    }

    it("should serialize and deserialize case classes of any arity") {
      case class State(name: String, population: Double)
      val SouthDakota = State("South Dakota", 835.175)

      assertCanBeSerializedAndDeserialized(SouthDakota)
    }

    it("should serialize and deserialize tuples of 1 to 22 elements") {
      val tuple = (1, 2, 3, 4)

      assertCanBeSerializedAndDeserialized(tuple)
    }

    it("should serialize and deserialize arrays") {
      val primes: Seq[Int] = Vector(2, 3, 5, 7, 11, 13)

      assertCanBeSerializedAndDeserialized(primes)
    }

    it("should serialize and deserialize structs") {
      val bounds = Map("lowerBound" -> 18, "upperBound" -> 139)

      assertCanBeSerializedAndDeserialized(bounds)
    }

    it("should deserialize a fault error from the server") {
      case class NonExistingResponse(a: Int)

      assert(Fault(4, "Too many parameters.") ===
        readXmlResponse[NonExistingResponse](
          <methodResponse>
            <fault>
              <value>
                <struct>
                  <member>
                    <name>faultCode</name>
                    <value><int>4</int></value>
                  </member>
                  <member>
                    <name>faultString</name>
                    <value><string>Too many parameters.</string></value>
                  </member>
                </struct>
              </value>
            </fault>
          </methodResponse>
        ).swap.toOption.get.head
      )
    }

    it("should serialize and deserialize Some (Option)") {
      val option = Some("Hello world")
      assertCanBeSerializedAndDeserialized(option)
    }

    it("should serialize and deserialize None (Option)") {
      val option = None
      assertCanBeSerializedAndDeserialized(option)
    }

    it("should support ISO8601 datetime serialization") {
      val currentDate = new Date()
      assertCanBeSerializedAndDeserialized(withoutMillis(currentDate))
    }

    it("should serialize and deserialize boolean") {
      val bool = true
      assertCanBeSerializedAndDeserialized(bool)
    }

    it("should serialize and deserialize base64") {
      val encodedMessage = "eW91IGNhbid0IHJlYWQgdGhpcyE=".getBytes
      assertCanBeSerializedAndDeserialized(encodedMessage)
    }

    it("should serialize and deserialize int") {
      val number = 41
      assertCanBeSerializedAndDeserialized(number)
    }

    it("should detect <i4> xml tag inside a value as a integer") {
      val number = 14

      assertResponseIsRead(number,
        <methodResponse>
          <params>
            <param>
              <value><i4>{number}</i4></value>
            </param>
          </params>
        </methodResponse>
      )
    }

    it("should serialize and deserialize double") {
      val double = 41.0
      assertCanBeSerializedAndDeserialized(double)
    }

    it("should support empty xml tag, representing a string, inside a value") {
      val greeting = "Hello World!"

      assertResponseIsRead(greeting,
        <methodResponse>
          <params>
            <param>
              <value>{greeting}</value>
            </param>
          </params>
        </methodResponse>
      )
    }

    it("should support void methodResponses") {
      assert(
        Void ===
          readXmlResponse[Empty](
            <methodResponse>
              <params>
              </params>
            </methodResponse>
          ).toOption.get
      )
    }

    it("should support null represented with <nil/>") {
      import XmlrpcProtocol.Null

      assert(
        scala.xml.Null ===
          readXmlResponse[Null](
            <methodResponse>
              <params>
                <param>
                  <value><nil/></value>
                </param>
              </params>
            </methodResponse>
          ).toOption.get
      )
    }

    it("should detect special characters in <string> and encode/decode them") {
      val message = "George & Bernard have < than you"

      assertResponseIsRead(message,
        <methodResponse>
          <params>
            <param>
              <value><string>{"George &amp Bernard have &lt than you"}</string></value>
            </param>
          </params>
        </methodResponse>
      )
    }
  }

  it("should not throw an exception when expecting a different response type") {
    val inEnglish = "Hello world!"
    val inSpanish = "Hola mundo!"
    val inGerman = "Hallo welt!"

    case class Greeting(english: String, spanish: String, german: String)

    try {
      readXmlResponse[Greeting](
        // This is an array
        <methodResponse>
          <params>
            <param>
              <array>
                <data>
                  <value>{inEnglish}</value>
                  <value>{inSpanish}</value>
                  <value>{inGerman}</value>
                </data>
              </array>
            </param>
          </params>
        </methodResponse>
      )
    } catch {
      case _: Throwable => fail("Should not produce an exception")
    }
  }
}
