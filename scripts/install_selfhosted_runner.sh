#!/usr/bin/env bash
set -euo pipefail

# Usage: ./install_selfhosted_runner.sh <GITHUB_URL> <RUNNER_TOKEN> <LABELS>
# Example:
# ./install_selfhosted_runner.sh https://github.com/my-org/my-repo ABC123 deploy-vm

GITHUB_URL=${1:-}
RUNNER_TOKEN=${2:-}
LABELS=${3:-deploy-vm}
RUNNER_DIR=${4:-/home/github-runner/actions-runner}
RUNNER_NAME=${5:-deploy-vm}

if [ -z "$GITHUB_URL" ] || [ -z "$RUNNER_TOKEN" ]; then
  echo "Usage: $0 <GITHUB_URL> <RUNNER_TOKEN> [LABELS] [RUNNER_DIR] [RUNNER_NAME]"
  exit 1
fi

mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"

echo "Downloading latest runner..."
LATEST_URL=$(curl -s https://api.github.com/repos/actions/runner/releases/latest | jq -r '.assets[] | select(.name|test("actions-runner-linux-x64")) | .browser_download_url')
if [ -z "$LATEST_URL" ]; then
  echo "Could not determine latest runner URL; ensure 'jq' is installed or set a fixed URL." >&2
  exit 1
fi
curl -sL -o actions-runner.tar.gz "$LATEST_URL"
tar xzf actions-runner.tar.gz
rm -f actions-runner.tar.gz

echo "Configuring runner..."
./config.sh --url "$GITHUB_URL" --token "$RUNNER_TOKEN" --labels "$LABELS" --name "$RUNNER_NAME" --unattended

# Install as a service (systemd)
sudo ./svc.sh install
sudo ./svc.sh start

echo "Runner installed and started (dir=$RUNNER_DIR, labels=$LABELS)."
echo "Remember: registration token is one-time; keep the runner service secured and update periodically."