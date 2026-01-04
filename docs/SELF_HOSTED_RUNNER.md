# Self-hosted runner setup (deploy-vm)

This document describes how to prepare an Azure VM to host a GitHub Actions self-hosted runner
to run `deploy.yml` so `docker compose` runs persistently on the VM.

## Prerequisites
- Ubuntu (18.04/20.04/22.04) or similar
- Docker & Docker Compose plugin installed
- User with sudo privileges
- `jq` (optional for the install script)

## Steps
1. Install Docker (example for Ubuntu):
   ```bash
   sudo apt update && sudo apt install -y ca-certificates curl gnupg lsb-release
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
   echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
   sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
   ```

2. Create runner user and add to docker group:
   ```bash
   sudo adduser --disabled-login --gecos "" github-runner
   sudo usermod -aG docker github-runner
   ```

3. Obtain registration token:
   - On GitHub: Settings → Actions → Runners → New self-hosted runner
   - Choose repository or organization level, copy the registration token.

4. On the VM (as `github-runner` or via sudo), run:
   ```bash
   ./scripts/install_selfhosted_runner.sh https://github.com/<OWNER>/<REPO> <REG_TOKEN> deploy-vm
   ```

5. Verify runner connected in GitHub UI and check `docker --version`, `docker compose version`.

## Hardening & maintenance tips
- Restrict which repositories/workflows can use the runner (GitHub settings).
- Keep OS/Docker updated; run periodic `docker system prune` and monitor disk usage.
- Consider enabling automatic reboot/restart of the runner service.
- Use GitHub Environments to control which workflows can access secrets.
