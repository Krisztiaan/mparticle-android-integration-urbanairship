package com.mparticle.kits;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Base64;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.analytics.InstallReceiver;
import com.urbanairship.analytics.RetailEventTemplate;
import com.urbanairship.push.GcmConstants;
import com.urbanairship.push.GcmPushReceiver;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProviderBridge;
import com.urbanairship.push.TagEditor;
import com.urbanairship.push.gcm.GcmPushProvider;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * mParticle-Urban Airship Kit integration
 */
public class UrbanAirshipKit extends KitIntegration
    implements KitIntegration.PushListener, KitIntegration.EventListener,
    KitIntegration.CommerceListener, KitIntegration.AttributeListener {

  public static final String CHANNEL_ID_INTEGRATION_KEY = "com.urbanairship.channel_id";
  // Identities
  private static final String IDENTITY_EMAIL = "email";
  private static final String IDENTITY_FACEBOOK = "facebook_id";
  private static final String IDENTITY_TWITTER = "twitter_id";
  private static final String IDENTITY_GOOGLE = "google_id";
  private static final String IDENTITY_MICROSOFT = "microsoft_id";
  private static final String IDENTITY_YAHOO = "yahoo_id";
  private static final String IDENTITY_FACEBOOK_CUSTOM_AUDIENCE_ID = "facebook_custom_audience_id";
  private static final String IDENTITY_CUSTOMER_ID = "customer_id";
  private ChannelIdListener channelIdListener;
  private UrbanAirshipConfiguration configuration;

  @Override public String getName() {
    return "Urban Airship";
  }

  @Override public Object getInstance() {
    return channelIdListener;
  }

  @Override protected List<ReportingMessage> onKitCreate(
      Map<String, String> settings,
      final Context context) {
    setUrbanConfiguration(new UrbanAirshipConfiguration(settings));
    channelIdListener = new ChannelIdListener() {

      @Override public void channelIdUpdated() {
        updateChannelIntegration();
      }
    };
    MParticleAutopilot.updateConfig(context, configuration);
    Autopilot.automaticTakeOff(context);
    updateChannelIntegration();
    return null;
  }

  void setUrbanConfiguration(UrbanAirshipConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override public void onSettingsUpdated(Map<String, String> settings) {
    setUrbanConfiguration(new UrbanAirshipConfiguration(settings));
  }

  @Override public List<ReportingMessage> setOptOut(boolean optedOut) {
    UAirship.shared().getAnalytics().setEnabled(optedOut);

    ReportingMessage message = new ReportingMessage(this,
        ReportingMessage.MessageType.OPT_OUT,
        System.currentTimeMillis(),
        null);
    return Collections.singletonList(message);
  }

  @Override public void setInstallReferrer(Intent intent) {
    new InstallReceiver().onReceive(UAirship.getApplicationContext(), intent);
  }

  @Override public boolean willHandlePushMessage(Intent intent) {
    return intent.getStringExtra(PushMessage.EXTRA_SEND_ID) != null
        || intent.getStringExtra(PushMessage.EXTRA_ALERT) != null;
  }

  @Override public void onPushMessageReceived(Context context, Intent intent) {
    new GcmPushReceiver() {
      private void normalizeIntent(Context context, Intent intent) {
        intent.setComponent((ComponentName) null);
        intent.setPackage(context.getPackageName());
        if (Build.VERSION.SDK_INT < 19) {
          intent.removeCategory(context.getPackageName());
        }

        String encodedData = intent.getStringExtra("gcm.rawData64");
        if (encodedData != null) {
          intent.putExtra("rawData", Base64.decode(encodedData, 0));
          intent.removeExtra("gcm.rawData64");
        }

        String from = intent.getStringExtra("from");
        if ("google.com/iid".equals(from) || "gcm.googleapis.com/refresh".equals(from)) {
          intent.setAction("com.google.android.gms.iid.InstanceID");
        }
      }

      private void startInstanceIdService(Context context, Intent intent) {
        ResolveInfo resolveInfo = context.getPackageManager().resolveService(intent, 0);
        if (resolveInfo != null && resolveInfo.serviceInfo != null) {
          ServiceInfo serviceInfo = resolveInfo.serviceInfo;
          if (context.getPackageName().equals(serviceInfo.packageName)
              && serviceInfo.name != null) {
            String serviceName = serviceInfo.name;
            serviceName =
                serviceName.startsWith(".") ? context.getPackageName() + serviceName : serviceName;
            Logger.debug("GcmPushReceiver - Forwarding GCM intent to " + serviceName);
            intent.setClassName(context.getPackageName(), serviceName);
          } else {
            Logger.error(
                "GcmPushReceiver - Error resolving target intent service, skipping classname enforcement. Resolved service was: "
                    + serviceInfo.packageName
                    + "/"
                    + serviceInfo.name);
          }
        }

        try {
          ComponentName componentName = startWakefulService(context, intent);
          if (this.isOrderedBroadcast()) {
            this.setResultCode(componentName == null ? 404 : -1);
          }
        } catch (SecurityException | IllegalStateException var6) {
          Logger.error("GcmPushReceiver - Error while delivering the message to the serviceIntent",
              var6);
          if (this.isOrderedBroadcast()) {
            this.setResultCode(401);
          }
        }
      }

      public void onReceive(Context context, Intent intent) {
        Autopilot.automaticTakeOff(context);
        Logger.verbose("GcmPushReceiver - Received intent: " + intent.getAction());
        this.normalizeIntent(context, intent);
        final boolean isOrderedBroadcast = this.isOrderedBroadcast();
        if (intent.getAction() == null) {
          if (isOrderedBroadcast) {
            this.setResultCode(0);
          }
        } else {
          String var5 = intent.getAction();
          byte var6 = -1;
          switch (var5.hashCode()) {
            case 366519424:
              if (var5.equals("com.google.android.c2dm.intent.RECEIVE")) {
                var6 = 0;
              }
              break;
            case 1111963330:
              if (var5.equals("com.google.android.gms.iid.InstanceID")) {
                var6 = 1;
              }
              break;
            case 1736128796:
              if (var5.equals("com.google.android.c2dm.intent.REGISTRATION")) {
                var6 = 2;
              }
          }

          switch (var6) {
            case 0:
              if (intent.getExtras() == null) {
                Logger.warn("GcmPushReceiver - Received push with null extras, dropping message");
                if (isOrderedBroadcast) {
                  this.setResultCode(0);
                }

                return;
              }

              final PendingResult result = this.goAsync();
              PushProviderBridge.receivedPush(context,
                  GcmPushProvider.class,
                  new PushMessage(intent.getExtras()),
                  new Runnable() {
                    public void run() {
                      if (result != null) {
                        if (isOrderedBroadcast) {
                          result.setResultCode(-1);
                        }

                        result.finish();
                      }
                    }
                  });
              break;
            case 1:
              startInstanceIdService(context, intent);
              if (isOrderedBroadcast) {
                this.setResultCode(-1);
              }
              break;
            case 2:
              PushProviderBridge.requestRegistrationUpdate(context);
              if (isOrderedBroadcast) {
                this.setResultCode(-1);
              }
          }
        }
      }
    }.onReceive(context, intent);
  }

  @Override public boolean onPushRegistration(String instanceId, String senderId) {
    // Assume mismatch sender Ids means we need to refresh the token
    if (!senderId.equals(UAirship.shared().getPushManager().getRegistrationToken())) {
      Intent intent = new Intent(GcmConstants.ACTION_GCM_REGISTRATION);
      new GcmPushReceiver().onReceive(getContext(), intent);
    }

    return true;
  }

  @Override public List<ReportingMessage> leaveBreadcrumb(String s) {
    return null;
  }

  @Override public List<ReportingMessage> logError(String s, Map<String, String> map) {
    return null;
  }

  @Override
  public List<ReportingMessage> logException(Exception e, Map<String, String> map, String s) {
    return null;
  }

  @Override public List<ReportingMessage> logEvent(MPEvent event) {
    Set<String> tagSet = extractTags(event);
    if (tagSet != null && tagSet.size() > 0) {
      UAirship.shared().getPushManager().editTags().addTags(tagSet).apply();
    }
    logUrbanAirshipEvent(event);
    return Collections.singletonList(ReportingMessage.fromEvent(this, event));
  }

  @Override
  public List<ReportingMessage> logScreen(String screenName, Map<String, String> attributes) {
    Set<String> tagSet = extractScreenTags(screenName, attributes);
    if (tagSet != null && tagSet.size() > 0) {
      UAirship.shared().getPushManager().editTags().addTags(tagSet).apply();
    }
    UAirship.shared().getAnalytics().trackScreen(screenName);

    ReportingMessage message = new ReportingMessage(this,
        ReportingMessage.MessageType.SCREEN_VIEW,
        System.currentTimeMillis(),
        attributes);
    return Collections.singletonList(message);
  }

  @Override public List<ReportingMessage> logLtvIncrease(
      BigDecimal valueIncreased,
      BigDecimal totalValue,
      String eventName,
      Map<String, String> contextInfo) {
    CustomEvent customEvent =
        new CustomEvent.Builder(eventName).setEventValue(valueIncreased).create();

    UAirship.shared().getAnalytics().addEvent(customEvent);

    ReportingMessage message = new ReportingMessage(this,
        ReportingMessage.MessageType.EVENT,
        System.currentTimeMillis(),
        contextInfo);
    return Collections.singletonList(message);
  }

  @Override public List<ReportingMessage> logEvent(CommerceEvent commerceEvent) {
    Set<String> tagSet = extractCommerceTags(commerceEvent);
    if (tagSet != null && tagSet.size() > 0) {
      UAirship.shared().getPushManager().editTags().addTags(tagSet).apply();
    }

    List<ReportingMessage> messages = new LinkedList<>();

    if (logAirshipRetailEvents(commerceEvent)) {
      messages.add(ReportingMessage.fromEvent(this, commerceEvent));
    } else {
      for (MPEvent event : CommerceEventUtils.expand(commerceEvent)) {
        logUrbanAirshipEvent(event);
        messages.add(ReportingMessage.fromEvent(this, event));
      }
    }

    return messages;
  }

  @Override public void setUserIdentity(MParticle.IdentityType identityType, String identity) {
    String airshipId = getAirshipIdentifier(identityType);
    if (airshipId != null) {
      UAirship.shared()
          .getAnalytics()
          .editAssociatedIdentifiers()
          .addIdentifier(airshipId, identity)
          .apply();
    }
  }

  @Override public void removeUserIdentity(MParticle.IdentityType identityType) {
    String airshipId = getAirshipIdentifier(identityType);
    if (airshipId != null) {
      UAirship.shared()
          .getAnalytics()
          .editAssociatedIdentifiers()
          .removeIdentifier(airshipId)
          .apply();
    }
  }

  @Override public void setUserAttribute(String key, String value) {
    if (configuration.getEnableTags()) {
      if (KitUtils.isEmpty(value)) {
        UAirship.shared()
            .getPushManager()
            .editTags()
            .addTag(KitUtils.sanitizeAttributeKey(key))
            .apply();
      } else if (configuration.getIncludeUserAttributes()) {
        UAirship.shared()
            .getPushManager()
            .editTags()
            .addTag(KitUtils.sanitizeAttributeKey(key) + "-" + value)
            .apply();
      }
    }
  }

  @Override public void setUserAttributeList(String s, List<String> list) {
    // not supported
  }

  @Override public boolean supportsAttributeLists() {
    return false;
  }

  @Override public void setAllUserAttributes(
      Map<String, String> stringAttributes,
      Map<String, List<String>> listAttributes) {
    if (configuration.getEnableTags()) {
      TagEditor editor = UAirship.shared().getPushManager().editTags();
      for (Map.Entry<String, String> entry : stringAttributes.entrySet()) {
        if (KitUtils.isEmpty(entry.getValue())) {
          editor.addTag(KitUtils.sanitizeAttributeKey(entry.getKey()));
        } else if (configuration.getIncludeUserAttributes()) {
          editor.addTag(KitUtils.sanitizeAttributeKey(entry.getKey()) + "-" + entry.getValue());
        }
      }
      editor.apply();
    }
  }

  @Override public void removeUserAttribute(String attribute) {
    UAirship.shared().getPushManager().editTags().removeTag(attribute).apply();
  }

  @Override public List<ReportingMessage> logout() {
    // not supported
    return null;
  }

  /**
   * Logs Urban Airship RetailEvents from a CommerceEvent.
   *
   * @param event The commerce event.
   * @return {@code true} if retail events were able to be generated from the CommerceEvent,
   * otherwise {@code false}.
   */
  private boolean logAirshipRetailEvents(CommerceEvent event) {
    if (event.getProductAction() == null || event.getProducts().isEmpty()) {
      return false;
    }

    switch (event.getProductAction()) {
      case Product.PURCHASE:
        for (Product product : event.getProducts()) {
          RetailEventTemplate template = RetailEventTemplate.newPurchasedTemplate();
          if (event.getTransactionAttributes() != null
              && !KitUtils.isEmpty(event.getTransactionAttributes().getId())) {
            template.setTransactionId(event.getTransactionAttributes().getId());
          }
          populateRetailEventTemplate(template, product).createEvent().track();
        }

        break;

      case Product.ADD_TO_CART:
        for (Product product : event.getProducts()) {
          populateRetailEventTemplate(RetailEventTemplate.newAddedToCartTemplate(),
              product).createEvent().track();
        }

        break;

      case Product.CLICK:
        for (Product product : event.getProducts()) {
          populateRetailEventTemplate(RetailEventTemplate.newBrowsedTemplate(),
              product).createEvent().track();
        }

        break;

      case Product.ADD_TO_WISHLIST:
        for (Product product : event.getProducts()) {
          populateRetailEventTemplate(RetailEventTemplate.newStarredProductTemplate(),
              product).createEvent().track();
        }
        break;
      default:
        return false;
    }

    return true;
  }

  /**
   * Populates an Urban Airship RetailEventTemplate from a product.
   *
   * @param template The retail event template.
   * @param product The product.
   * @return The populated retail event template.
   */
  private RetailEventTemplate populateRetailEventTemplate(
      RetailEventTemplate template,
      Product product) {
    return template.setCategory(product.getCategory())
        .setId(product.getSku())
        .setDescription(product.getName())
        .setValue(product.getTotalAmount())
        .setBrand(product.getBrand());
  }

  /**
   * Logs an Urban Airship CustomEvent from an MPEvent.
   *
   * @param event The MPEvent.
   */
  private void logUrbanAirshipEvent(MPEvent event) {
    CustomEvent.Builder eventBuilder = new CustomEvent.Builder(event.getEventName());
    if (event.getInfo() != null) {
      for (Map.Entry<String, String> entry : event.getInfo().entrySet()) {
        eventBuilder.addProperty(entry.getKey(), entry.getValue());
      }
    }

    UAirship.shared().getAnalytics().addEvent(eventBuilder.create());
  }

  Set<String> extractTags(MPEvent event) {
    Set<String> tags = new HashSet<String>();
    if (configuration.getEventClass() != null && configuration.getEventClass()
        .containsKey(event.getEventHash())) {
      tags.addAll(configuration.getEventClass().get(event.getEventHash()));
    }
    if (configuration.getEventAttributeClass() != null && event.getInfo() != null) {
      for (Map.Entry<String, String> attribute : event.getInfo().entrySet()) {
        int hash = KitUtils.hashForFiltering(event.getEventType().ordinal()
            + event.getEventName()
            + attribute.getKey());
        List<String> tagValues = configuration.getEventAttributeClass().get(hash);
        if (tagValues != null) {
          tags.addAll(tagValues);
          if (!KitUtils.isEmpty(attribute.getValue())) {
            for (String tagValue : tagValues) {
              tags.add(tagValue + "-" + attribute.getValue());
            }
          }
        }
      }
    }
    return tags;
  }

  Set<String> extractCommerceTags(CommerceEvent commerceEvent) {
    Set<String> tags = new HashSet<String>();

    int commerceEventHash =
        KitUtils.hashForFiltering(CommerceEventUtils.getEventType(commerceEvent) + "");
    if (configuration.getEventClassDetails() != null && configuration.getEventClassDetails()
        .containsKey(commerceEventHash)) {
      tags.addAll(configuration.getEventClassDetails().get(commerceEventHash));
    }

    if (configuration.getEventAttributeClassDetails() != null) {
      List<MPEvent> expandedEvents = CommerceEventUtils.expand(commerceEvent);
      for (MPEvent event : expandedEvents) {
        if (event.getInfo() != null) {
          for (Map.Entry<String, String> attribute : event.getInfo().entrySet()) {
            int hash =
                KitUtils.hashForFiltering(CommerceEventUtils.getEventType(commerceEvent) + attribute
                    .getKey());
            List<String> tagValues = configuration.getEventAttributeClassDetails().get(hash);
            if (tagValues != null) {
              tags.addAll(tagValues);
              if (!KitUtils.isEmpty(attribute.getValue())) {
                for (String tagValue : tagValues) {
                  tags.add(tagValue + "-" + attribute.getValue());
                }
              }
            }
          }
        }
      }
    }
    return tags;
  }

  Set<String> extractScreenTags(String screenName, Map<String, String> attributes) {
    Set<String> tags = new HashSet<String>();
    int screenEventHash = KitUtils.hashForFiltering("0" + screenName);
    if (configuration.getEventClassDetails() != null && configuration.getEventClassDetails()
        .containsKey(screenEventHash)) {
      tags.addAll(configuration.getEventClassDetails().get(screenEventHash));
    }
    if (configuration.getEventAttributeClassDetails() != null && attributes != null) {
      for (Map.Entry<String, String> attribute : attributes.entrySet()) {
        int hash = KitUtils.hashForFiltering("0" + screenName + attribute.getKey());
        List<String> tagValues = configuration.getEventAttributeClassDetails().get(hash);
        if (tagValues != null) {
          tags.addAll(tagValues);
        }
        if (!KitUtils.isEmpty(attribute.getValue())) {
          for (String tagValue : tagValues) {
            tags.add(tagValue + "-" + attribute.getValue());
          }
        }
      }
    }
    return tags;
  }

  /**
   * Maps MParticle.IdentityType to an Urban Airship device identifier.
   *
   * @param identityType The mParticle identity type.
   * @return The Urban Airship identifier, or {@code null} if one does not exist.
   */
  private String getAirshipIdentifier(MParticle.IdentityType identityType) {
    switch (identityType) {
      case CustomerId:
        return IDENTITY_CUSTOMER_ID;

      case Facebook:
        return IDENTITY_FACEBOOK;

      case Twitter:
        return IDENTITY_TWITTER;

      case Google:
        return IDENTITY_GOOGLE;

      case Microsoft:
        return IDENTITY_MICROSOFT;

      case Yahoo:
        return IDENTITY_YAHOO;

      case Email:
        return IDENTITY_EMAIL;

      case FacebookCustomAudienceId:
        return IDENTITY_FACEBOOK_CUSTOM_AUDIENCE_ID;
    }

    return null;
  }

  /**
   * Sets the Urban Airship Channel ID as an mParticle integration attribute.
   */
  private void updateChannelIntegration() {
    String channelId = UAirship.shared().getPushManager().getChannelId();
    if (!KitUtils.isEmpty(channelId)) {
      HashMap<String, String> integrationAttributes = new HashMap<String, String>(1);
      integrationAttributes.put(UrbanAirshipKit.CHANNEL_ID_INTEGRATION_KEY, channelId);
      setIntegrationAttributes(integrationAttributes);
    }
  }

  public interface ChannelIdListener {
    void channelIdUpdated();
  }
}