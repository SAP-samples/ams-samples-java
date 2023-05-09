#!/bin/bash

readonly java_dir=java-security-ams
readonly spring_dir=spring-security-ams

username=$(whoami | tr '[:upper:]' '[:lower:]')

function assertSuccess {
    local exitStatus=$1
    local errMsg=$2
    if [ $exitStatus -ne 0 ]; then
      echo $errMsg
      exit 1
    fi
}

function check_output_with_timeout {
  local timeout=$1
  local interval=$2
  local cmd=$3
  local success_output=$4
  local elapsed=0
  while true; do
    output=$(eval $cmd)
    if [ $output -eq "$success_output" ]; then
      #echo "No apps found. Continuing..."
      break
    fi
    if [ $elapsed -ge $timeout ]; then
      echo "Timeout reached. Aborting..."
      exit 1
    fi
    sleep $interval
    elapsed=$((elapsed + interval))
  done
}

function deploy_app {
  local folder=$1
  local service_instance_name=$2

  echo
  echo "This script will now deploy the sample app by"
  echo "  1. Creating the AMS service instance using 'cf create-service identity application $service_instance_name -c $folder/ias-config.json'"
  echo "  2. Deploying the actual application using 'cf push -f $folder/manifest.yml --vars-file vars.yml'"
  echo "You can use these commands also manually to re-deploy the app later."
  read -p "Please press any key to continue..." -n 1 -r
  echo

  ## Check if the service instance already exists
  cf service "$service_instance_name"  > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    read -p "The AMS service instance '$service_instance_name' already exists in your space. Do you want to use the sample with this instance? Y/n " -n 1 -r
    echo # Move to a new line
    if [[ ! $REPLY =~ ^[Yy]$ ]] && [[ ! -z $REPLY ]]; then
      echo "Please delete the instance manually or restart this script and select space cleaning."
      exit 1
    fi
  else
    ## Create AMS/IAS service instance
    sed -e "s/((ID))/$username/g; s/((LANDSCAPE_APPS_DOMAIN))/$landscape_apps_domain/g" "$folder"/ias-config.json.template > "$folder"/ias-config.json
    cf create-service identity application "$service_instance_name" -c "$folder"/ias-config.json
    echo "Waiting for AMS service instance creation to be finished... (90sec timeout)"
    check_output_with_timeout 90 3 "cf service $service_instance_name | grep 'status:\s*create succeeded' | wc -l" 1
    echo "Service instance creation finished successfully"
  fi

  cf push -f "$folder"/manifest.yml --vars-file vars.yml
  assertSuccess $? "The application deployment has failed "
}

function check_maven_installation {
  # Check mvn installation
  mvn --version > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    echo "There is no valid MVN installation on your machine. This is required for the Java sample app. Please install it now and restart this script. (e.g. on macOS via Homebrew 'brew install maven')"
    exit 1
  fi

  m2_home="${M2_HOME:-$HOME/.m2}"
  m2_set=$(grep -c '<url>https://int.repositories.cloud.sap/artifactory/' "$m2_home"/settings.xml)
  if [[ "$m2_set" -eq 0 ]]; then
    echo
    echo "Your Maven installation is not configured to use the SAP internal artifactory. This is required for this script to run."
    read -p "Do you want this script to automatically download 'https://int.repositories.cloud.sap/artifactory/build-releases/settings.xml' to ~/.m2/settings.xml? Y/n " -n 1 -r
    if [[ ! $REPLY =~ ^[Yy]$ ]] && [[ ! -z $REPLY ]]; then
      echo "Please download the settings  delete the instance manually or restart this script and select space cleaning."
      exit 1
    else
      curl https://int.repositories.cloud.sap/artifactory/build-releases/settings.xml > "$m2_home/settings.xml"
    fi
  fi
}

function prepare_spring_deployment {
  check_maven_installation

  echo
  echo "Running 'mvn clean package -DskipTests --batch-mode'"
  (cd "$spring_dir" && mvn clean package -DskipTests --batch-mode)
}

function prepare_java_deployment {
  check_maven_installation

  echo
  read -p "This Java sample app supports multi-tenancy with one demo subscriber. This requires additional setup! Would you like to activate multi-tenancy? (optionally) y/N " -n 1 -r
  echo # Move to a new line
  if [[ ! $REPLY =~ ^[Nn]$ ]] && [[ ! -z $REPLY ]]; then
    echo "The multi-tenant setup is not yet fully supported by this script. Please restart without multi-tenancy"
    exit 1
    # TODO multi tenant setup is not yet complete
    cp "$java_dir/manifest-multi-tenant.yml" "$java_dir/manifest.yml"
    echo "Continuing with multi-tenancy enabled"

      provider_tenant_name=$(echo vars.yml | grep -o '^PROVIDER_TENANT_NAME: ')
      provider_tenant_id=$(echo vars.yml | grep -o '^PROVIDER_TENANT_ID: ')
      subscriber_tenant_name=$(echo vars.yml | grep -o '^SUBSCRIBER_TENANT_NAME: ')
      subscriber_tenant_id=$(echo vars.yml | grep -o '^SUBSCRIBER_TENANT_ID: ')

      if [[ $provider_tenant_name == '""' ]]; then
        read -p "Please enter the IAS tenant name to which you established trust in your the PROVIDER subaccount. (This is where this sample application is deployed).  " -r provider_tenant_name
        sed -i '' -e "s/^PROVIDER_TENANT_NAME: .*/PROVIDER_TENANT_NAME: $provider_tenant_name/" vars.yml
      fi
      if [[ $provider_tenant_id == '""' ]]; then
        read -p "Please enter the tenant ID of your provider subaccount (this is where this sample application is deployed). You can find this on the Overview page of your subaccount in the BTP Cockpit. " -r provider_tenant_name
        # TODO
      fi
      if [[ $subscriber_tenant_name == '""' ]]; then
        read -p "Please enter the IAS tenant name to which you established trust in your the SUBSCRIBER subaccount. (This can be any subaccount in the same global account as your provider subaccount).  " -r provider_tenant_name
        # TODO
      fi
      if [[ $subscriber_tenant_id == '""' ]]; then
        read -p "Please enter the tenant ID of your subscriber subaccount. " -r provider_tenant_name

        # TODO
      fi
  else
    cp "$java_dir/manifest-single-tenant.yml" "$java_dir/manifest.yml"
    echo "Continuing without multi-tenancy"
  fi

  echo
  echo "Running 'mvn clean package -DskipTests --batch-mode'"
  (cd "$java_dir" && mvn clean package -DskipTests --batch-mode)
}

echo
echo "Hi $username"!
echo
echo "Welcome to the Authorization Management Service (AMS) sample apps!"
echo "This wizard will guide you through the configuration and deployment of one of the sample apps."
echo
read -p "Please press any key to start the setup..." -n 1 -r
echo


# Check if jq is installed
jq --version > /dev/null 2>&1
assertSuccess $? "'jq' (CLI for JSON processing) is required for this script to run. Please install it first (e.g. on macOS via Homebrew 'brew install jq')"

# Check if the CF CLI is installed
cf --version > /dev/null 2>&1
assertSuccess $? "The Cloud Foundry CLI is not installed. Please install it first (e.g. on macOS via Homebrew 'brew install cf-cli@8')"

# Check connection to SAP network
curl -sSf https://int.repositories.cloud.sap > /dev/null
assertSuccess $? "You must be in the SAP network to build the samples. Please connect to VPN."

# Check if the user is logged in
cf orgs > /dev/null 2>&1
assertSuccess $? "You are not logged in to Cloud Foundry with the CF CLI. Please log in using 'cf login'"

# Check if a space is targeted
cf apps > /dev/null 2>&1
assertSuccess $? "You have not targeted a CF org and space. Please select your target."

cf target

# Ask the user for confirmation
read -p "Do you want to continue deployment in the above target? Y/n " -n 1 -r
echo # Move to a new line
if [[ ! $REPLY =~ ^[Yy]$ ]] && [[ ! -z $REPLY ]]; then
  echo "Aborting... Please select your target org and space via 'cf target -o <ORG> -s <SPACE>'"
  exit 0
fi

# Possiblity to change the username
read -p "Using username '$username' to personalize your app route. Do you want to continue with this name? Y/n " -n 1 -r
echo # Move to a new line
if [[ ! $REPLY =~ ^[Yy]$ ]] && [[ ! -z $REPLY ]]; then
  read -p "Please enter your username to personalize your app route: " -r username
  echo
  while [ -z "$username"  ]; do # Username empty
    read -p "Username must not be empty. Please choose another username: " -r username
    echo # empty line
  done
fi

landscape_apps_domain=$(cf domains | grep -o '^cfapps\.[a-z0-9]\+\.hana\.ondemand\.com')

sed -i '' -e "s/^ID: .*/ID: $username/" vars.yml
sed -i '' -e "s/^LANDSCAPE_APPS_DOMAIN: .*/LANDSCAPE_APPS_DOMAIN: $landscape_apps_domain/" vars.yml

# Clean space
cf_org=$(jq -r ".OrganizationFields.Name" ~/.cf/config.json)
cf_space_name=$(jq -r ".SpaceFields.Name" ~/.cf/config.json)
cf_space_guid=$(jq -r ".SpaceFields.GUID" ~/.cf/config.json)

apps=$(cf apps | tail -n +4 | awk '{print $1}')
num_apps=$(echo "$apps" | wc -w | awk '{print $1}')

services=$(cf services | tail -n +4 | awk '{print $1}')
num_services=$(echo "$services" | wc -w | awk '{print $1}')

if [[ $num_apps -gt 0 ]] || [[ $num_services -gt 0 ]]; then
  read -p "There are currently $num_apps apps and $num_services services deployed in your space. Do you want to clean up the space before deploying the new Sample App? y/N " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]] ; then
    # Delete service bindings
    service_instances_guid_json=$(cf curl "/v3/service_instances?space_guids=$cf_space_guid" | jq '[.resources[].guid]')
    service_instances_guid_commasep=$(echo $service_instances_guid_json | jq -r 'join(",")')
    get_service_bindings_cmd="cf curl '/v3/service_credential_bindings?service_instance_guids=$service_instances_guid_commasep' | jq -r '.resources[].guid'"
    service_bindings_guid=$(eval "$get_service_bindings_cmd")

    echo "Deleting service bindings"
    for binding_guid in $service_bindings_guid; do
      cf curl -X DELETE "/v3/service_credential_bindings/$binding_guid"
    done

    echo "Waiting for all service bindings to be deleted... (90sec timeout)"
    check_output_with_timeout 90 3 "$get_service_bindings_cmd | wc -l" 0

    # Delete each app
    for app in $apps
    do
      cf delete "$app" -f
    done
    # delete each service
    for service in $services
    do
      cf delete-service "$service" -f
    done

    echo "Waiting for all apps and services to be deleted... (90sec timeout)"
    check_output_with_timeout 90 3 "cf services | tail -n +4 | awk '{print \$1}' | wc -w" 0
    echo "All service instances deleted successfully"
  fi
fi


while true; do
  echo "Which Sample App do you want to deploy?"
  echo "1. Java"
  echo "2. Spring"
  echo
  read -p "App (number or name): " -r input
  input=$(echo "$input" | tr '[:upper:]' '[:lower:]')
  case "$input" in
    1|java)
      choice=java
      echo "Continuing with the deployment of the Java sample app."
      prepare_java_deployment
      deploy_app $java_dir java-ams-ias
      break
      ;;
    2|spring)
      choice=spring
      echo "Continuing with the deployment of the Spring sample app."
      prepare_spring_deployment
      deploy_app $spring_dir spring-ams-ias
      break
      ;;
    *)
      echo "Invalid option, please try again."
      ;;
  esac
done


approuter_urls=$(cf apps | grep "$choice" | grep approuter | grep -o "\S*$landscape_apps_domain" | sed 's|^|https://|')
for approuter_url in $approuter_urls; do
  approuter_string+=$approuter_url
done
# remove last comma
approuter_string=$(echo "$approuter_string" | sed 's/, $//')

echo
echo "Success!"
echo "Your sample application is now deployed. Check it out using 'cf apps' and access it at $approuter_string"