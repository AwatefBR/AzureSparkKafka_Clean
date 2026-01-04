# Guide Terraform et Git - Bonnes Pratiques

## ✅ Ce qui DOIT être sur Git

### 1. Fichiers de configuration Terraform
- ✅ `*.tf` (main.tf, variables.tf, outputs.tf, etc.)
- ✅ `*.tfvars` (fichiers de variables par environnement)
- ✅ `.terraform.lock.hcl` (lockfile des versions de providers)

**Pourquoi ?** Ces fichiers définissent votre infrastructure et doivent être versionnés pour :
- Traçabilité des changements
- Collaboration en équipe
- Reproducibilité
- Code review

### 2. Fichiers dans votre projet
```
infra/
├── main.tf              ✅ Sur Git
├── variables.tf         ✅ Sur Git
├── outputs.tf           ✅ Sur Git
├── .terraform.lock.hcl   ✅ Sur Git
└── envs/
    ├── dev/
    │   └── terraform.tfvars  ✅ Sur Git
    └── prod/
        └── terraform.tfvars  ✅ Sur Git
```

## ❌ Ce qui NE DOIT PAS être sur Git

### 1. Dossier `.terraform/`
- ❌ `.terraform/` (providers téléchargés, ~200-300 MB)
- ❌ `.terraform/providers/` (binaires des providers)

**Pourquoi ?** 
- Fichiers volumineux (dépassent la limite GitHub de 100 MB)
- Générés automatiquement par `terraform init`
- Spécifiques à chaque machine/OS
- Peuvent être régénérés à tout moment

### 2. Fichiers de state
- ❌ `*.tfstate` (state local)
- ❌ `*.tfstate.*` (backups de state)

**Pourquoi ?**
- Contiennent des informations sensibles (credentials, IDs)
- Peuvent causer des conflits entre développeurs
- Doivent être stockés dans un backend distant sécurisé (Azure Storage, S3, etc.)

### 3. Fichiers sensibles
- ❌ `.env` (variables d'environnement avec secrets)
- ❌ Fichiers avec credentials en clair

## 📋 Configuration `.gitignore` recommandée

```gitignore
# Terraform
.terraform/              # Providers téléchargés
*.tfstate               # State files
*.tfstate.*             # Backups de state
*.tfvars.backup         # Backups de variables
.terraform.tfstate.lock.info  # Lock files

# Note: .terraform.lock.hcl DOIT être commité
```

## 🔄 Workflow recommandé

### 1. Premier setup
```bash
cd infra
terraform init          # Télécharge les providers dans .terraform/
terraform plan          # Vérifie la configuration
```

### 2. Avant chaque commit
```bash
# Vérifier que .terraform/ n'est pas commité
git status | grep .terraform

# Si présent, le retirer
git rm -r --cached .terraform/
```

### 3. Sur une nouvelle machine
```bash
git clone <repo>
cd infra
terraform init          # Régénère .terraform/ automatiquement
```

## 🚀 Guide pour le Push (après nettoyage)

### Étape 1 : Vérifier l'état
```bash
git status
git log --oneline -5
```

### Étape 2 : S'assurer que tout est propre
```bash
# Vérifier qu'il n'y a plus de fichiers volumineux
git ls-files | xargs ls -lh | awk '$5 ~ /M/ {print}'

# Vérifier que .terraform/ n'est pas suivi
git ls-files | grep ".terraform/"
```

### Étape 3 : Force push (nécessaire après filter-branch)
```bash
# Force push vers votre branche
git push --force origin spark-clean-devops
```

**⚠️ Attention** : Le force push réécrit l'historique. Si d'autres personnes travaillent sur ce repo :
- Ils devront re-cloner : `git clone <repo>`
- Ou mettre à jour leur branche : `git fetch origin && git reset --hard origin/spark-clean-devops`

### Étape 4 : Vérifier que le push a réussi
```bash
# Vérifier sur GitHub que le push est passé
# Les fichiers volumineux ne devraient plus apparaître
```

## 📊 Résumé : Votre situation actuelle

### ✅ Sur Git (correct)
- `infra/main.tf`
- `infra/variables.tf`
- `infra/outputs.tf`
- `infra/.terraform.lock.hcl`
- `infra/envs/dev/terraform.tfvars`
- `infra/envs/prod/terraform.tfvars`

### ❌ Pas sur Git (correct)
- `.terraform/` (ignoré par `.gitignore`)
- `*.tfstate` (ignoré par `.gitignore`)

## 🎯 Bonnes Pratiques Résumées

1. **Code Terraform** → ✅ Sur Git
2. **Providers Terraform** → ❌ Pas sur Git (régénérés avec `terraform init`)
3. **State files** → ❌ Pas sur Git (utiliser un backend distant)
4. **Lock file** → ✅ Sur Git (pour garantir les versions)
5. **Variables par env** → ✅ Sur Git (mais sans secrets)

## 🔐 Sécurité

- Ne jamais commiter les secrets dans `.tfvars`
- Utiliser des variables d'environnement ou Azure Key Vault pour les secrets
- Utiliser un backend distant pour le state (Azure Storage avec chiffrement)



