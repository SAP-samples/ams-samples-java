# SPDX-FileCopyrightText: 2018-2021 SAP SE or an SAP affiliate company and Cloud Security Client Java contributors
# SPDX-License-Identifier: Apache-2.0
#!/usr/bin/env bash
# Replace ID placeholder in the configs files for ias and sms services
ID=$(awk '/ID:/ {print $2; exit}' ../vars.yml)
DOMAIN=$(awk '/LANDSCAPE_APPS_DOMAIN:/ {print $2; exit}' ../vars.yml)
MT=$(awk '/MT:/ {print $2; exit}' ../vars.yml)

timer=200
progress=""
# Colors
RED='\033[0;31m'
RS='\033[0m' # Reset style
GREEN='\033[0;32m'
CYAN='\033[96m'

prompt_wait () {
  read -p "Identity service creation is taking a while... Do you want to continue waiting?(y/n) " wait
                case $wait in
                    [Yy]* ) (( timer=timer+50 )); return;;
                    [Nn]* ) echo "Exiting... Run the script again after some while"; exit;;
                    * ) echo "Please answer Y/y for yes or N/n for no"; prompt_wait;;
                esac
}

wait_for_ias () {
  echo "$progress"
  while [ "$timer" -gt 0 ] && [[ $progress == *"in progress"* ]]; do
      printf "Waiting until Identity instance$CYAN jakarta-ams-ias$RS is created. %s seconds left\n" "$timer"
      progress=$(cf service jakarta-ams-ias | grep "status:")
      printf "%s\n" "$progress"
      (( timer=timer-5 ))
      sleep 5
      if [[ timer -eq 0 ]];then
          prompt_wait
      fi
  done
}

# Create IAS service instance
printf "Creating identity service$CYAN jakarta-ams-ias$RS\n"
progress=$(cf cs identity application jakarta-ams-ias -c "$(sed "s/((ID))/$ID/;s/((LANDSCAPE_APPS_DOMAIN))/$DOMAIN/;s/((MT))/$MT/g" ./ias-config.json)")
# cf service creation status options: "create failed"| "already exists" | "in progress" | "succeeded"
if [[ $progress != *"already exists"* ]]; then
  wait_for_ias
  printf "Identity service$CYAN jakarta-ams-ias$RS was created successfully\n"
elif [[ $progress == *"FAILED"* ]]; then
  printf $progress
else
  printf "Identity service$CYAN jakarta-ams-ias$RS already exists\n"
fi


# Create SMS service instance if IAS was created successfully
if [[ $progress ==  *"succeeded"* || $progress ==  *"already exists"* ]]; then
  printf "Creating subscription manager service$CYAN jakarta-ams-sms$RS\n"
  sms_progress=$(cf cs subscription-manager provider jakarta-ams-sms -c "$(sed "s/((ID))/$ID/;s/((LANDSCAPE_APPS_DOMAIN))/$DOMAIN/g" ./sms-config.json)")
  if [[ $sms_progress != *"already exists"* ]]; then
      sms_progress=$(cf service jakarta-ams-sms | grep "status:")
      while [[ $sms_progress == *"in progress"* || ($sms_progress != *"succeeded"* && $sms_progress != *"failed"* ) ]]; do
          sms_progress=$(cf service jakarta-ams-sms | grep "status:")
          printf "%s\n" "$sms_progress"
          sleep 5
      done
      if [[ $sms_progress == *"succeeded"*  ]]; then
          printf "Subscription manager service$CYAN jakarta-ams-sms$RS created successfully\n"
      fi
  else
      printf "Subscription manager service$CYAN jakarta-ams-sms$RS already exists\n"
  fi
elif [[ $progress == *"failed"* ]]; then
  printf "Couldn't create Identity service$CYAN jakarta-ams-ias$RS: %s" "$progress"
fi
