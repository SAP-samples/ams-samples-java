import string
import time

from cloudfoundry_client.common_objects import JsonObject
from retry import retry
from util.common_requests import HttpUtil
from util.env import Environment

SMS_API_PATH = "/subscription-manager/v1/subscriptions/"
SAAS_API_PATH = "/saas-manager/v1/applications/"


class SubscriptionUrlError(Exception):
    pass


class SMSClient:

    @classmethod
    def get_sms_token(cls, sms_creds: JsonObject):
        return HttpUtil.get_token(
            token_url=sms_creds.get('url') + '/oauth/token',
            clientid=sms_creds.get('clientid'),
            clientsecret=sms_creds.get('clientsecret'),
            grant_type='client_credentials').get('access_token')

    @classmethod
    def get_cis_token(cls, org_name: string):
        creds = Environment.cis_credentials()[org_name]

        return HttpUtil.get_token(
            token_url=creds['url'] + '/oauth/token',
            clientid=creds['clientid'],
            clientsecret=creds['clientsecret'],
            grant_type='password',
            username=Environment.username_def_idp(),
            password=Environment.password_def_idp()).get('access_token')

    @classmethod
    def subscribe(cls, sms_url, app_name, token):
        """
        Sends a POST request to the saas manager API - /saas-manager/v1/applications/{appName}/subscription
        :param sms_url: `subscription_manager_url` can be found subscription-manager credentials in app's VCAP_SERVICES
        :param app_name: `app_name` can be found subscription-manager credentials in app's VCAP_SERVICES
        :param token: token obtained from Cloud management service (cis) with plan: local in subscriber's subaccount
        :return: response from the API
        """
        headers = {'Authorization': 'Bearer ' + token,
                   'Content-Type': 'application/json'}
        res = HttpUtil.post_request(url=sms_url + SAAS_API_PATH + app_name + '/subscription',
                                    headers=headers)

        if res.status_code == 202:
            return res.status_code
        elif res.status_code == 409:
            print(f'Subscription already exists. '
                  f'All subscriptions should have been deleted before running the e2e tests')
            return res.status_code

    @classmethod
    def get_subscription_by_name(cls, sms_url, app_name, token):
        """
        Queries the saas manager API - /saas-manager/v1/applications/{appName}
        :param sms_url: `subscription_manager_url` can be found subscription-manager credentials in app's VCAP_SERVICES
        :param app_name: `app_name` can be found subscription-manager credentials in app's VCAP_SERVICES
        :param token: token obtained from Cloud management service (cis) with plan: local in subscriber's subaccount
        :return: response from the API
        """
        return HttpUtil.get_request(
            url=sms_url + SAAS_API_PATH + app_name,
            id_token=token)

    @classmethod
    @retry(SubscriptionUrlError, tries=7, delay=10)
    def get_subscription_url(cls, sms_url, app_name, token):
        subscription = cls.get_subscription_by_name(sms_url, app_name, token).json()
        print(f"Subscription for {app_name} "
              f"status is {subscription.get('state')}")
        if subscription.get('state') != 'SUBSCRIBED':
            raise SubscriptionUrlError(f"Subscription for {app_name} - "
                                       f"status {subscription.get('state')} != SUBSCRIBED, "
                                       f"subscriptionUrl is not available")
        else:
            return subscription.get('subscriptionUrl')

    @classmethod
    def get_subscription(cls, sms_url, subscription_id, token):
        return HttpUtil.get_request(
            url=sms_url + SMS_API_PATH + subscription_id,
            id_token=token)

    @classmethod
    def get_subscriptions(cls, sms_url, token):
        return HttpUtil.get_request(
            url=sms_url + SMS_API_PATH,
            id_token=token)

    @classmethod
    def poll_until_subscription_deleted(cls, sms_url, access_token, start_time, timeout=300):
        # get subscriptions
        get_res = cls.get_subscriptions(sms_url, access_token)

        # Raise an exception if the GET request failed
        if get_res.status_code != 200:
            raise Exception(f"GET subscriptions request failed - {get_res.status_code}: {get_res.text}")

        data = get_res.json()
        if len(data['subscriptions']) == 0:
            print("subscriptions deleted")
            return
        else: 
            # Raises an exception if the subscription is not deleted within the timeout
            if time.time() - start_time > timeout:
                raise Exception(f"Timeout of {timeout} seconds exceeded while polling for subscription deletion")
            time.sleep(10)

            print("subscriptions still exist, polling again")
            cls.poll_until_subscription_deleted(sms_url, access_token, start_time)

    @classmethod
    async def delete_subscriptions(cls, sms_creds: JsonObject):
        sms_url = sms_creds.get('subscription_manager_url')
        access_token = cls.get_sms_token(sms_creds)
        # get subscriptions
        get_res = cls.get_subscriptions(sms_url, access_token)
        if get_res.status_code == 200:
            data = get_res.json()
            if len(data['subscriptions']) == 0:
                print("no subscriptions to delete")
                return
        else:
            raise Exception(f"GET subscriptions request failed - {get_res.status_code}: {get_res.text}")

        for subscription in data['subscriptions']:
            subscription_id = subscription["subscriptionId"]
            if subscription_id != "":
                # delete subscriptions
                del_res = HttpUtil.delete_request(
                    url=sms_url + SMS_API_PATH + subscription_id,
                    id_token=access_token)
                if 200 <= del_res.status_code <= 299:  # it returns 202
                    cls.poll_until_subscription_deleted(sms_url, access_token, time.time())
                    print(
                        f"DELETE request for subscription with id '{subscription_id}' succeeded - status code:'{del_res.status_code}' ")
                else:
                    raise Exception(f"DELETE request for subscription with id '{subscription_id}' failed - {del_res.status_code}: {del_res.text}")
