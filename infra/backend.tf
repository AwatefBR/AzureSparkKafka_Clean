# Backend configuration for Terraform state
# This file defines where Terraform stores its state file
# The actual values are provided via backend-config.tfvars (not versioned)

terraform {
  backend "azurerm" {
    # Configuration will be provided via:
    # terraform init -backend-config=backend-config.tfvars
    # 
    # Required variables in backend-config.tfvars:
    #   resource_group_name  = "RG-SPARKLOL"
    #   storage_account_name = "tfstatelolXXXXX"
    #   container_name       = "tfstate"
    #   key                  = "prod.terraform.tfstate"
  }
}

