package org.mdedetrich.stripe.v1

import java.time.OffsetDateTime

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import defaults._
import enumeratum._
import io.circe.{Decoder, Encoder}
import org.mdedetrich.stripe.v1.defaults._
import org.mdedetrich.stripe.{ApiKey, Endpoint, IdempotencyKey, PostParams}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object Plans extends LazyLogging {

  sealed abstract class Interval(val id: String) extends EnumEntry {
    override val entryName = id
  }

  object Interval extends Enum[Interval] {
    val values = findValues

    case object Day   extends Interval("day")
    case object Week  extends Interval("week")
    case object Month extends Interval("month")
    case object Year  extends Interval("year")

    implicit val planIntervalDecoder: Decoder[Interval] = enumeratum.Circe.decoder(Interval)
    implicit val planIntervalEncoder: Encoder[Interval] = enumeratum.Circe.encoder(Interval)
  }

  sealed abstract class Status(val id: String) extends EnumEntry {
    override val entryName = id
  }

  object Status extends Enum[Status] {
    val values = findValues

    case object Trialing extends Status("trialing")
    case object Active   extends Status("active")
    case object PastDue  extends Status("past_due")
    case object Canceled extends Status("canceled")
    case object Unpaid   extends Status("unpaid")

    implicit val planStatusDecoder: Decoder[Status] = enumeratum.Circe.decoder(Status)
    implicit val planStatusEncoder: Encoder[Status] = enumeratum.Circe.encoder(Status)
  }

  /**
    * @see https://stripe.com/docs/api#plan_object
    * @param id
    * @param amount              The amount in cents to be charged on the interval specified
    * @param created
    * @param currency            Currency in which subscription will be charged
    * @param interval            One of [[Interval.Day]], [[Interval.Week]], [[Interval.Month]] or
    *                            [[Interval.Year]]. The frequency with which a subscription
    *                            should be billed.
    * @param intervalCount       The number of intervals (specified in the [[interval]]
    *                            property) between each subscription billing. For example,
    *                            \[[interval]]=[[Interval.Month]] and [[intervalCount]]=3
    *                            bills every 3 months.
    * @param livemode
    * @param name                Display name of the plan
    * @param metadata            A set of key/value pairs that you can attach to a plan object.
    *                            It can be useful for storing additional information about the
    *                            plan in a structured format.
    * @param statementDescriptor Extra information about a charge for the customer’s
    *                            credit card statement.
    * @param trialPeriodDays     Number of trial period days granted when
    *                            subscribing a customer to this plan.
    *                            [[scala.None]] if the plan has no trial period.
    */
  case class Plan(id: String,
                  amount: BigDecimal,
                  created: OffsetDateTime,
                  currency: Currency,
                  interval: Interval,
                  intervalCount: Long,
                  livemode: Boolean,
                  name: String,
                  metadata: Option[Map[String, String]] = None,
                  statementDescriptor: Option[String] = None,
                  trialPeriodDays: Option[Long] = None)

  implicit val planDecoder: Decoder[Plan] = Decoder.forProduct11(
    "id",
    "amount",
    "created",
    "currency",
    "interval",
    "interval_count",
    "livemode",
    "name",
    "metadata",
    "statement_descriptor",
    "trial_period_days"
  )(Plan.apply)

  implicit val planEncoder: Encoder[Plan] = Encoder.forProduct12(
    "id",
    "object",
    "amount",
    "created",
    "currency",
    "interval",
    "interval_count",
    "livemode",
    "name",
    "metadata",
    "statement_descriptor",
    "trial_period_days"
  )(
    x =>
      (x.id,
       "plan",
       x.amount,
       x.created,
       x.currency,
       x.interval,
       x.intervalCount,
       x.livemode,
       x.metadata,
       x.name,
       x.statementDescriptor,
       x.trialPeriodDays))

  /**
    * @see https://stripe.com/docs/api#create_plan
    * @param id                  Unique string of your choice that will be used
    *                            to identify this plan when subscribing a customer.
    *                            This could be an identifier like “gold” or a
    *                            primary key from your own database.
    * @param amount              A positive integer in cents (or 0 for a free plan)
    *                            representing how much to charge (on a recurring basis).
    * @param currency            3-letter ISO code for currency.
    * @param interval            Specifies billing frequency. Either [[Interval.Day]],
    *                            [[Interval.Week]], [[Interval.Month]] or [[Interval.Year]].
    * @param name                Name of the plan, to be displayed on invoices and in
    *                            the web interface.
    * @param intervalCount       The number of intervals between each subscription
    *                            billing. For example, [[interval]]=[[Interval.Month]]
    *                            and [[intervalCount]]=3 bills every 3 months. Maximum of
    *                            one year interval allowed (1 year, 12 months, or 52 weeks).
    * @param metadata            A set of key/value pairs that you can attach to a plan object.
    *                            It can be useful for storing additional information about
    *                            the plan in a structured format. This will be unset if you
    *                            POST an empty value.
    * @param statementDescriptor An arbitrary string to be displayed on your
    *                            customer’s credit card statement. This may be up to
    *                            22 characters. As an example, if your website is
    *                            RunClub and the item you’re charging for is your
    *                            Silver Plan, you may want to specify a [[statementDescriptor]]
    *                            of RunClub Silver Plan. The statement description may not include `<>"'`
    *                            characters, and will appear on your customer’s statement in
    *                            capital letters. Non-ASCII characters are automatically stripped.
    *                            While most banks display this information consistently,
    *                            some may display it incorrectly or not at all.
    * @param trialPeriodDays     Specifies a trial period in (an integer number of)
    *                            days. If you include a trial period, the customer
    *                            won’t be billed for the first time until the trial period ends.
    *                            If the customer cancels before the trial period is over,
    *                            she’ll never be billed at all.
    * @throws StatementDescriptorTooLong          - If [[statementDescriptor]] is longer than 22 characters
    * @throws StatementDescriptorInvalidCharacter - If [[statementDescriptor]] has an invalid character
    */
  case class PlanInput(id: String,
                       amount: BigDecimal,
                       currency: Currency,
                       interval: Interval,
                       name: String,
                       intervalCount: Option[Long] = None,
                       metadata: Option[Map[String, String]] = None,
                       statementDescriptor: Option[String] = None,
                       trialPeriodDays: Option[Long] = None) {
    statementDescriptor match {
      case Some(sD) if sD.length > 22 =>
        throw StatementDescriptorTooLong(sD.length)
      case Some(sD) if sD.contains("<") =>
        throw StatementDescriptorInvalidCharacter("<")
      case Some(sD) if sD.contains(">") =>
        throw StatementDescriptorInvalidCharacter(">")
      case Some(sD) if sD.contains("\"") =>
        throw StatementDescriptorInvalidCharacter("\"")
      case Some(sD) if sD.contains("\'") =>
        throw StatementDescriptorInvalidCharacter("\'")
      case _ =>
    }
  }

  implicit val planInputDecoder: Decoder[PlanInput] = Decoder.forProduct9(
    "id",
    "amount",
    "currency",
    "interval",
    "name",
    "interval_count",
    "metadata",
    "statement_descriptor",
    "trial_period_days"
  )(PlanInput.apply)

  implicit val planInputEncoder: Encoder[PlanInput] = Encoder.forProduct9(
    "id",
    "amount",
    "currency",
    "interval",
    "name",
    "interval_count",
    "metadata",
    "statement_descriptor",
    "trial_period_days"
  )(x => PlanInput.unapply(x).get)

  def create(planInput: PlanInput)(idempotencyKey: Option[IdempotencyKey] = None)(
      implicit apiKey: ApiKey,
      endpoint: Endpoint,
      client: HttpExt,
      materializer: Materializer,
      executionContext: ExecutionContext): Future[Try[Plan]] = {
    val postFormParameters = PostParams.flatten(
      Map(
        "id"                   -> Option(planInput.id.toString),
        "amount"               -> Option(planInput.amount.toString()),
        "currency"             -> Option(planInput.currency.iso.toLowerCase),
        "interval"             -> Option(planInput.interval.id.toString),
        "name"                 -> Option(planInput.name),
        "interval_count"       -> planInput.intervalCount.map(_.toString),
        "statement_descriptor" -> planInput.statementDescriptor,
        "trial_period_days"    -> planInput.trialPeriodDays.map(_.toString)
      )) ++ mapToPostParams(planInput.metadata, "metadata")

    logger.debug(s"Generated POST form parameters is $postFormParameters")

    val finalUrl = endpoint.url + "/v1/plans"

    createRequestPOST[Plan](finalUrl, postFormParameters, idempotencyKey, logger)
  }

  def get(id: String)(implicit apiKey: ApiKey,
                      endpoint: Endpoint,
                      client: HttpExt,
                      materializer: Materializer,
                      executionContext: ExecutionContext): Future[Try[Plan]] = {
    val finalUrl = endpoint.url + s"/v1/plans/$id"

    createRequestGET[Plan](finalUrl, logger)
  }

  def delete(id: String)(idempotencyKey: Option[IdempotencyKey] = None)(
      implicit apiKey: ApiKey,
      endpoint: Endpoint,
      client: HttpExt,
      materializer: Materializer,
      executionContext: ExecutionContext): Future[Try[DeleteResponse]] = {
    val finalUrl = endpoint.url + s"/v1/plans/$id"

    createRequestDELETE(finalUrl, idempotencyKey, logger)
  }

  /**
    * @see https://stripe.com/docs/api#list_plans
    * @param created       A filter on the list based on the object
    *                      [[created]] field. The value can be a string
    *                      with an integer Unix timestamp, or it can be a
    *                      dictionary with the following options:
    * @param endingBefore  A cursor for use in pagination. [[endingBefore]] is an
    *                      object ID that defines your place in the list.
    *                      For instance, if you make a list request and
    *                      receive 100 objects, starting with obj_bar,
    *                      your subsequent call can include [[endingBefore]]=obj_bar
    *                      in order to fetch the previous page of the list.
    * @param limit         A limit on the number of objects to be returned.
    *                      Limit can range between 1 and 100 items.
    * @param startingAfter A cursor for use in pagination. [[startingAfter]] is
    *                      an object ID that defines your place in the list.
    *                      For instance, if you make a list request and receive 100
    *                      objects, ending with obj_foo, your subsequent call
    *                      can include [[startingAfter]]=obj_foo in order to
    *                      fetch the next page of the list.
    */
  case class PlanListInput(created: Option[ListFilterInput] = None,
                           endingBefore: Option[String] = None,
                           limit: Option[Long] = None,
                           startingAfter: Option[String] = None)

  case class PlanList(override val url: String,
                      override val hasMore: Boolean,
                      override val data: List[Plan],
                      override val totalCount: Option[Long])
      extends Collections.List[Plan](url, hasMore, data, totalCount)

  object PlanList extends Collections.ListJsonMappers[Plan] {
    implicit val planListDecoder: Decoder[PlanList] =
      listDecoder(implicitly)(PlanList.apply)

    implicit val planListEncoder: Encoder[PlanList] =
      listEncoder[PlanList]
  }

  def list(planListInput: PlanListInput, includeTotalCount: Boolean)(
      implicit apiKey: ApiKey,
      endpoint: Endpoint,
      client: HttpExt,
      materializer: Materializer,
      executionContext: ExecutionContext): Future[Try[PlanList]] = {
    val finalUrl = {

      val baseUrl = endpoint.url + s"/v1/plans"

      val created: Uri = planListInput.created match {
        case Some(createdInput) =>
          listFilterInputToUri(createdInput, baseUrl, "created")
        case None => baseUrl
      }

      val queries = PostParams.flatten(
        List(
          "ending_before"  -> planListInput.endingBefore,
          "limit"          -> planListInput.limit.map(_.toString),
          "starting_after" -> planListInput.startingAfter
        ))

      val query = queries.foldLeft(created.query())((a, b) => b +: a)
      created.withQuery(query)
    }

    createRequestGET[PlanList](finalUrl, logger)
  }
}
