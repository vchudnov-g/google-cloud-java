/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pubsub;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.GrpcServiceOptions;
import com.google.cloud.Policy;
import com.google.cloud.pubsub.PubSub.MessageConsumer;
import com.google.cloud.pubsub.PubSub.MessageProcessor;
import com.google.cloud.pubsub.PubSub.PullOption;
import com.google.common.base.Function;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

/**
 * A Google Cloud Pub/Sub subscription. A subscription represents the stream of messages from a
 * single, specific topic, to be delivered to the subscribing application. Pub/Sub subscriptions
 * support both push and pull message delivery.
 *
 * <p>In a push subscription, the Pub/Sub server sends a request to the subscriber application, at a
 * preconfigured endpoint (see {@link PushConfig}). The subscriber's HTTP response serves as an
 * implicit acknowledgement: a success response indicates that the message has been succesfully
 * processed and the Pub/Sub system can delete it from the subscription; a non-success response
 * indicates that the Pub/Sub server should resend it (implicit "nack").
 *
 * <p>In a pull subscription, the subscribing application must explicitly pull messages using one of
 * {@link PubSub#pull(String, int)}, {@link PubSub#pullAsync(String, int)} or
 * {@link PubSub#pullAsync(String, PubSub.MessageProcessor callback, PubSub.PullOption...)}.
 * When messages are pulled with {@link PubSub#pull(String, int)} or
 * {@link PubSub#pullAsync(String, int)} the subscribing application must also explicitly
 * acknowledge them using one of {@link PubSub#ack(String, Iterable)},
 * {@link PubSub#ack(String, String, String...)}, {@link PubSub#ackAsync(String, Iterable)} or
 * {@link PubSub#ackAsync(String, String, String...)}.
 *
 * <p>{@code Subscription} adds a layer of service-related functionality over
 * {@link SubscriptionInfo}. Objects of this class are immutable. To get a {@code Subscription}
 * object with the most recent information use {@link #reload} or {@link #reloadAsync}.
 *
 * @see <a href="https://cloud.google.com/pubsub/overview#data_model">Pub/Sub Data Model</a>
 * @see <a href="https://cloud.google.com/pubsub/subscriber">Subscriber Guide</a>
 */
public class Subscription extends SubscriptionInfo {

  private static final long serialVersionUID = -4153366055659552230L;

  private final PubSubOptions options;
  private transient PubSub pubsub;

  /**
   * A builder for {@code Subscription} objects.
   */
  public static final class Builder extends SubscriptionInfo.Builder {

    private final PubSub pubsub;
    private final BuilderImpl delegate;

    private Builder(Subscription subscription) {
      pubsub = subscription.pubsub;
      delegate = new BuilderImpl(subscription);
    }

    @Override
    public Builder topic(TopicId topic) {
      delegate.topic(topic);
      return this;
    }

    @Override
    public Builder topic(String project, String topic) {
      delegate.topic(project, topic);
      return this;
    }

    @Override
    public Builder topic(String topic) {
      delegate.topic(topic);
      return this;
    }

    @Override
    public Builder name(String name) {
      delegate.name(name);
      return this;
    }

    @Override
    public Builder pushConfig(PushConfig pushConfig) {
      delegate.pushConfig(pushConfig);
      return this;
    }

    @Override
    public Builder ackDeadLineSeconds(int ackDeadLineSeconds) {
      delegate.ackDeadLineSeconds(ackDeadLineSeconds);
      return this;
    }

    @Override
    public Subscription build() {
      return new Subscription(this.pubsub, this.delegate);
    }
  }

  Subscription(PubSub pubsub, BuilderImpl builder) {
    super(builder);
    this.pubsub = checkNotNull(pubsub);
    options = pubsub.options();
  }

  @Override
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(options, super.hashCode());
  }

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || !obj.getClass().equals(Subscription.class)) {
      return false;
    }
    Subscription other = (Subscription) obj;
    return baseEquals(other) && Objects.equals(options, other.options);
  }

  /**
   * Returns the subscription's {@code PubSub} object used to issue requests.
   */
  public PubSub pubSub() {
    return pubsub;
  }

  /**
   * Deletes this subscription.
   *
   * @return {@code true} if the subscription was deleted, {@code false} if it was not found
   * @throws PubSubException upon failure
   */
  public boolean delete() {
    return pubsub.deleteSubscription(name());
  }

  /**
   * Sends a request for deleting this subscription. This method returns a {@code Future} object to
   * consume the result. {@link Future#get()} returns {@code true} if the subscription was deleted,
   * {@code false} if it was not found.
   */
  public Future<Boolean> deleteAsync() {
    return pubsub.deleteSubscriptionAsync(name());
  }

  /**
   * Fetches current subscription's latest information. Returns {@code null} if the subscription
   * does not exist.
   *
   * @return a {@code Subscription} object with latest information or {@code null} if not found
   * @throws PubSubException upon failure
   */
  public Subscription reload() {
    return pubsub.getSubscription(name());
  }

  /**
   * Sends a request for fetching current subscription's latest information. This method returns a
   * {@code Future} object to consume the result. {@link Future#get()} returns the requested
   * subscription or {@code null} if not found.
   *
   * @return a {@code Subscription} object with latest information or {@code null} if not found
   * @throws PubSubException upon failure
   */
  public Future<Subscription> reloadAsync() {
    return pubsub.getSubscriptionAsync(name());
  }

  /**
   * Sets the push configuration for this subscription. This may be used to change a push
   * subscription to a pull one (passing a {@code null} {@code pushConfig} parameter) or vice versa.
   * This methods can also be used to change the endpoint URL and other attributes of a push
   * subscription. Messages will accumulate for delivery regardless of changes to the push
   * configuration.
   *
   * @param pushConfig the new push configuration. Use {@code null} to unset it
   * @throws PubSubException upon failure, or if the subscription does not exist
   */
  public void replacePushConfig(PushConfig pushConfig) {
    pubsub.replacePushConfig(name(), pushConfig);
  }

  /**
   * Sends a request for updating the push configuration for a specified subscription. This may be
   * used to change a push subscription to a pull one (passing a {@code null} {@code pushConfig}
   * parameter) or vice versa. This methods can also be used to change the endpoint URL and other
   * attributes of a push subscription. Messages will accumulate for delivery regardless of changes
   * to the push configuration. The method returns a {@code Future} object that can be used to wait
   * for the replace operation to be completed.
   *
   * @param pushConfig the new push configuration. Use {@code null} to unset it
   * @return a {@code Future} to wait for the replace operation to be completed.
   */
  public Future<Void> replacePushConfigAsync(PushConfig pushConfig) {
    return pubsub.replacePushConfigAsync(name(), pushConfig);
  }

  /**
   * Pulls messages from this subscription. This method possibly returns no messages if no message
   * was available at the time the request was processed by the Pub/Sub service (i.e. the system is
   * not allowed to wait until at least one message is available). Pulled messages have their
   * acknowledge deadline automatically renewed until they are explicitly consumed using
   * {@link Iterator#next()}.
   *
   * <p>Example usage of synchronous message pulling:
   * <pre> {@code
   * Iterator<ReceivedMessage> messageIterator = pubsub.pull("subscription", 100);
   * while (messageIterator.hasNext()) {
   *   ReceivedMessage message = messageIterator.next();
   *   // message's acknowledge deadline is no longer automatically renewed. If processing takes
   *   // long pubsub.modifyAckDeadline(String, String, long, TimeUnit) can be used to extend it.
   *   doSomething(message);
   *   message.ack(); // or message.nack()
   * }}</pre>
   *
   * @param maxMessages the maximum number of messages pulled by this method. This method can
   *     possibly return fewer messages.
   * @throws PubSubException upon failure
   */
  public Iterator<ReceivedMessage> pull(int maxMessages) {
    return pubsub.pull(name(), maxMessages);
  }

  /**
   * Sends a request for pulling messages from this subscription. This method returns a
   * {@code Future} object to consume the result. {@link Future#get()} returns a message iterator.
   * This method possibly returns no messages if no message was available at the time the request
   * was processed by the Pub/Sub service (i.e. the system is not allowed to wait until at least one
   * message is available).
   *
   * <p>Example usage of asynchronous message pulling:
   * <pre> {@code
   * Future<Iterator<ReceivedMessage>> future = pubsub.pull("subscription", 100);
   * // do something while the request gets processed
   * Iterator<ReceivedMessage> messageIterator = future.get();
   * while (messageIterator.hasNext()) {
   *   ReceivedMessage message = messageIterator.next();
   *   // message's acknowledge deadline is no longer automatically renewed. If processing takes
   *   // long pubsub.modifyAckDeadline(String, String, long, TimeUnit) can be used to extend it.
   *   doSomething(message);
   *   message.ack(); // or message.nack()
   * }}</pre>
   *
   * @param maxMessages the maximum number of messages pulled by this method. This method can
   *     possibly return fewer messages.
   * @throws PubSubException upon failure
   */
  public Future<Iterator<ReceivedMessage>> pullAsync(int maxMessages) {
    return pubsub.pullAsync(name(), maxMessages);
  }

  /**
   * Creates a message consumer that pulls messages from this subscription. You can stop pulling
   * messages by calling {@link MessageConsumer#close()}. The returned message consumer executes
   * {@link MessageProcessor#process(Message)} on each pulled message. If
   * {@link MessageProcessor#process(Message)} executes correctly, the message is acknowledged. If
   * {@link MessageProcessor#process(Message)} throws an exception, the message is "nacked". For
   * all pulled messages, the ack deadline is automatically renewed until the message is either
   * acknowledged or "nacked".
   *
   * <p>The {@link PullOption#maxQueuedCallbacks(int)} option can be used to control the maximum
   * number of queued messages (messages either being processed or waiting to be processed). The
   * {@link PullOption#executorFactory(GrpcServiceOptions.ExecutorFactory)} can be used to provide
   * an executor to run message processor callbacks.
   *
   * @param callback the callback to be executed on each message
   * @param options pulling options
   * @return a message consumer for the provided subscription and options
   */
  public MessageConsumer pullAsync(MessageProcessor callback, PullOption... options) {
    return pubsub.pullAsync(name(), callback, options);
  }

  /**
   * Returns the IAM access control policy for this subscription. Returns {@code null} if the
   * subscription was not found.
   *
   * @throws PubSubException upon failure
   */
  public Policy getPolicy() {
    return pubsub.getSubscriptionPolicy(this.name());
  }

  /**
   * Sends a request for getting the IAM access control policy for this subscription. This method
   * returns a {@code Future} object to consume the result. {@link Future#get()} returns the
   * requested policy or {@code null} if the subscription was not found.
   *
   * @throws PubSubException upon failure
   */
  public Future<Policy> getPolicyAsync() {
    return pubsub.getSubscriptionPolicyAsync(this.name());
  }

  /**
   * Sets the IAM access control policy for this subscription. Replaces any existing policy. This
   * method returns the new policy.
   *
   * <p>It is recommended that you use the read-modify-write pattern. This pattern entails reading
   * the project's current policy, updating it locally, and then sending the modified policy for
   * writing. Cloud IAM solves the problem of conflicting processes simultaneously attempting to
   * modify a policy by using the {@link Policy#etag etag} property. This property is used to
   * verify whether the policy has changed since the last request. When you make a request with an
   * etag value, the value in the request is compared with the existing etag value associated with
   * the policy. The policy is written only if the etag values match. If the etags don't match, a
   * {@code PubSubException} is thrown, denoting that the server aborted update. If an etag is not
   * provided, the policy is overwritten blindly.
   *
   * @throws PubSubException upon failure
   */
  public Policy replacePolicy(Policy newPolicy) {
    return pubsub.replaceSubscriptionPolicy(this.name(), newPolicy);
  }

  /**
   * Sends a request to set the IAM access control policy for this subscription. Replaces any
   * existing policy. This method returns a {@code Future} object to consume the result.
   * {@link Future#get()} returns the new policy.
   *
   * <p>It is recommended that you use the read-modify-write pattern. This pattern entails reading
   * the project's current policy, updating it locally, and then sending the modified policy for
   * writing. Cloud IAM solves the problem of conflicting processes simultaneously attempting to
   * modify a policy by using the {@link Policy#etag etag} property. This property is used to
   * verify whether the policy has changed since the last request. When you make a request with an
   * etag value, the value in the request is compared with the existing etag value associated with
   * the policy. The policy is written only if the etag values match. If the etags don't match,
   * {@link Future#get()} will throw a {@link java.util.concurrent.ExecutionException} caused by a
   * {@code PubSubException}, denoting that the server aborted update. If an etag is not provided,
   * the policy is overwritten blindly.
   *
   * @throws PubSubException upon failure
   */
  public Future<Policy> replacePolicyAsync(Policy newPolicy) {
    return pubsub.replaceSubscriptionPolicyAsync(this.name(), newPolicy);
  }

  /**
   * Returns the permissions that a caller has on this subscription. You typically don't call this
   * method if you're using Google Cloud Platform directly to manage permissions. This method is
   * intended for integration with your proprietary software, such as a customized graphical user
   * interface. For example, the Cloud Platform Console tests IAM permissions internally to
   * determine which UI should be available to the logged-in user.
   *
   * @return A list of booleans representing whether the caller has the permissions specified (in
   *     the order of the given permissions)
   * @throws PubSubException upon failure
   * @see <a href="https://cloud.google.com/pubsub/docs/access_control#permissions">
   *     Permissions and Roles</a>
   */
  public List<Boolean> testPermissions(List<String> permissions) {
    return pubsub.testSubscriptionPermissions(this.name(), permissions);
  }

  /**
   * Sends a request to get the permissions that a caller has on this subscription.
   *
   * <p>You typically don't call this method if you're using Google Cloud Platform directly to
   * manage permissions. This method is intended for integration with your proprietary software,
   * such as a customized graphical user interface. For example, the Cloud Platform Console tests
   * IAM permissions internally to determine which UI should be available to the logged-in user.
   *
   * @return A {@code Future} object to consume the result. {@link Future#get()} returns a list of
   *     booleans representing whether the caller has the permissions specified (in the order of the
   *     given permissions)
   * @throws PubSubException upon failure
   * @see <a href="https://cloud.google.com/pubsub/docs/access_control#permissions">
   *     Permissions and Roles</a>
   */
  public Future<List<Boolean>> testPermissionsAsync(List<String> permissions) {
    return pubsub.testSubscriptionPermissionsAsync(this.name(), permissions);
  }

  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    this.pubsub = options.service();
  }

  static Subscription fromPb(PubSub storage, com.google.pubsub.v1.Subscription subscriptionPb) {
    SubscriptionInfo subscriptionInfo = SubscriptionInfo.fromPb(subscriptionPb);
    return new Subscription(storage, new BuilderImpl(subscriptionInfo));
  }

  static Function<com.google.pubsub.v1.Subscription, Subscription> fromPbFunction(
      final PubSub pubsub) {
    return new Function<com.google.pubsub.v1.Subscription, Subscription>() {
      @Override
      public Subscription apply(com.google.pubsub.v1.Subscription subscriptionPb) {
        return subscriptionPb != null ? fromPb(pubsub, subscriptionPb) : null;
      }
    };
  }
}
