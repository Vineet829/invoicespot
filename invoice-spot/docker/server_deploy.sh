# Ensure GOOGLE_CLOUD_VM_IP is set
if [ -z "$GOOGLE_CLOUD_VM_IP" ]
then
  echo "GOOGLE_CLOUD_VM_IP not defined"
  exit 0
fi

# Variables for VM name and GCP project ID. Replace these with your actual VM name and project ID.
VM_NAME='instance-20240422-225019'
GCP_PROJECT='payscribe'
VM_ZONE='asia-south2-a' # Add the zone of your VM

# Archive the project
git archive --format tar --output ./project.tar main

echo 'Uploading project.........:-)...Be Patient!'
# Use gcloud compute scp to transfer the file to the VM
gcloud compute scp ./project.tar root@$VM_NAME:/tmp/project.tar --zone=$VM_ZONE --project=$GCP_PROJECT
echo 'Upload complete'

echo 'Building the image...'
# Use gcloud compute ssh to execute commands on the VM
gcloud compute ssh root@$VM_NAME --zone=$VM_ZONE --project=$GCP_PROJECT --command='
  mkdir -p /app
  rm -rf /app/* && tar -xf /tmp/project.tar -C /app
  docker compose -f /app/production.yml build
'
echo 'Build completed successfully.'