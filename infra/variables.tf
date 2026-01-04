variable "rg_name" {
  description = "Nom du Resource Group existant"
  type        = string
}

variable "vm_name" {
  description = "Nom de la VM existante"
  type        = string
}

variable "vm_nic_name" {
  description = "Nom de la NIC associée à la VM"
  type        = string
}

variable "vm_public_ip_name" {
  description = "Nom de la ressource Public IP"
  type        = string
}

variable "vnet_name" {
  description = "Nom du Virtual Network existant"
  type        = string
}

variable "subnet_name" {
  description = "Nom du subnet existant"
  type        = string
}

variable "sql_server_name" {
  description = "Nom du serveur Azure SQL (sans .database.windows.net)"
  type        = string
}

variable "sql_database_name" {
  description = "Nom de la base de données Azure SQL"
  type        = string
}

variable "subscription_id" {
  description = "ID de la subscription Azure"
  type        = string
}

variable "tenant_id" {
  description = "ID du tenant Azure"
  type        = string
}

variable "sql_admin_password" {
  description = "Mot de passe de l'administrateur SQL Server"
  type        = string
  sensitive   = true
}