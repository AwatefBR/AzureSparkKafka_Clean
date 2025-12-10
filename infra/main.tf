terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # backend "azurerm" { ... }  # on verra plus tard pour le state distant
}

provider "azurerm" {
  features {}

  subscription_id = var.subscription_id
  tenant_id       = var.tenant_id
}

# -------------------------
# Resource Group existant
# -------------------------
data "azurerm_resource_group" "rg" {
  name = var.rg_name
}

# -------------------------
# VM existante + réseau
# -------------------------

#data "azurerm_linux_virtual_machine" "vm" {
# name                = var.vm_name
#  resource_group_name = data.azurerm_resource_group.rg.name
#}

data "azurerm_network_interface" "vm_nic" {
  name                = var.vm_nic_name
  resource_group_name = data.azurerm_resource_group.rg.name
}

data "azurerm_public_ip" "vm_public_ip" {
  name                = var.vm_public_ip_name
  resource_group_name = data.azurerm_resource_group.rg.name
}

data "azurerm_virtual_network" "vnet" {
  name                = var.vnet_name
  resource_group_name = data.azurerm_resource_group.rg.name
}

data "azurerm_subnet" "subnet" {
  name                 = var.subnet_name
  virtual_network_name = data.azurerm_virtual_network.vnet.name
  resource_group_name  = data.azurerm_resource_group.rg.name
}

# -------------------------
# Azure SQL existant
# -------------------------

data "azurerm_mssql_server" "sql_server" {
  name                = var.sql_server_name
  resource_group_name = data.azurerm_resource_group.rg.name
}

data "azurerm_mssql_database" "sql_db" {
  name      = var.sql_database_name
  server_id = data.azurerm_mssql_server.sql_server.id
}

# -------------------------
# Outputs
# -------------------------

#output "vm_name" {
#  value       = data.azurerm_linux_virtual_machine.vm.name
#  description = "Nom de la VM existante"
#}

output "vm_public_ip" {
  value       = data.azurerm_public_ip.vm_public_ip.ip_address
  description = "IP publique de la VM (pour SSH / déploiement)"
}

output "vm_private_ip" {
  value       = data.azurerm_network_interface.vm_nic.ip_configuration[0].private_ip_address
  description = "IP privée de la VM dans le VNet"
}

output "vnet_name" {
  value       = data.azurerm_virtual_network.vnet.name
  description = "Nom du VNet"
}

output "subnet_name" {
  value       = data.azurerm_subnet.subnet.name
  description = "Nom du subnet"
}

output "sql_server_fqdn" {
  value       = data.azurerm_mssql_server.sql_server.fully_qualified_domain_name
  description = "FQDN du serveur SQL (host pour la connexion)"
}

output "sql_database_name" {
  value       = data.azurerm_mssql_database.sql_db.name
  description = "Nom de la base de données SQL"
}
