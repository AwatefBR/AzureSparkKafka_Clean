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
# Réseau - Converti en resources pour gestion
# -------------------------

# Virtual Network
resource "azurerm_virtual_network" "vnet" {
  name                = var.vnet_name
  location            = "switzerlandnorth"
  resource_group_name = data.azurerm_resource_group.rg.name
  address_space       = ["172.17.0.0/16"]

  tags = {
  ManagedBy    = "Terraform"
  Environment  = "Production"
  LastUpdated  = "2025-01-15"
  }
}

# Subnet
resource "azurerm_subnet" "subnet" {
  name                 = var.subnet_name
  resource_group_name  = data.azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.vnet.name
  address_prefixes     = ["172.17.0.0/24"]
}

# Public IP
resource "azurerm_public_ip" "vm_public_ip" {
  name                = var.vm_public_ip_name
  location            = "switzerlandnorth"
  resource_group_name = data.azurerm_resource_group.rg.name
  allocation_method   = "Static"
  sku                 = "Standard"
  zones               = ["1"]
}

# -------------------------
# Network Security Group (data source - on ne le gère pas)
# -------------------------

data "azurerm_network_security_group" "vm_nsg" {
  name                = "lol-vm-nsg"
  resource_group_name = data.azurerm_resource_group.rg.name
}

# -------------------------
# Network Interface - Converti en resource pour gestion
# -------------------------

resource "azurerm_network_interface" "vm_nic" {
  name                = var.vm_nic_name
  location            = "switzerlandnorth"
  resource_group_name = data.azurerm_resource_group.rg.name

  ip_configuration {
    name                          = "ipconfig1"
    subnet_id                     = azurerm_subnet.subnet.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.vm_public_ip.id
  }
}

# Attacher le NSG à la NIC (séparément car c'est une association)
resource "azurerm_network_interface_security_group_association" "vm_nic_nsg" {
  network_interface_id      = azurerm_network_interface.vm_nic.id
  network_security_group_id = data.azurerm_network_security_group.vm_nsg.id
}

# -------------------------
# Virtual Machine (temporairement en data, à convertir plus tard)
# -------------------------

#data "azurerm_linux_virtual_machine" "vm" {
# name                = var.vm_name
#  resource_group_name = data.azurerm_resource_group.rg.name
#}

# -------------------------
# Azure SQL - Converti en resources pour gestion
# -------------------------

# SQL Server
resource "azurerm_mssql_server" "sql_server" {
  name                          = var.sql_server_name
  resource_group_name           = data.azurerm_resource_group.rg.name
  location                      = "switzerlandnorth"
  version                       = "12.0"
  administrator_login           = "dbadmin"
  administrator_login_password  = var.sql_admin_password
  minimum_tls_version           = "1.2"
  public_network_access_enabled = true

  tags = {}
}

# SQL Database
resource "azurerm_mssql_database" "sql_db" {
  name      = var.sql_database_name
  server_id = azurerm_mssql_server.sql_server.id
  collation = "SQL_Latin1_General_CP1_CI_AS"
  # Note: license_type n'est pas supporté pour les bases serverless (GP_S_Gen5_1)
  max_size_gb    = 3
  sku_name       = "GP_S_Gen5_1"
  zone_redundant = false

  # Ignorer les propriétés read-only ou non configurables
  lifecycle {
    ignore_changes = [
      storage_account_type, # Propriété read-only, gérée par Azure
    ]
  }

  tags = {}
}

# -------------------------
# Outputs
# -------------------------

#output "vm_name" {
#  value       = data.azurerm_linux_virtual_machine.vm.name
#  description = "Nom de la VM existante"
#}

output "vm_public_ip" {
  value       = azurerm_public_ip.vm_public_ip.ip_address
  description = "IP publique de la VM (pour SSH / déploiement)"
}

output "vm_private_ip" {
  value       = azurerm_network_interface.vm_nic.ip_configuration[0].private_ip_address
  description = "IP privée de la VM dans le VNet"
}

output "vnet_name" {
  value       = azurerm_virtual_network.vnet.name
  description = "Nom du VNet"
}

output "subnet_name" {
  value       = azurerm_subnet.subnet.name
  description = "Nom du subnet"
}

output "sql_server_fqdn" {
  value       = azurerm_mssql_server.sql_server.fully_qualified_domain_name
  description = "FQDN du serveur SQL (host pour la connexion)"
}

output "sql_database_name" {
  value       = azurerm_mssql_database.sql_db.name
  description = "Nom de la base de données SQL"
}
