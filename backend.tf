terraform {
  required_version = ">= 1.0.0"

  backend "remote" {
    organization = "yudapinhas"

    workspaces {
      name = "dev"
    }
  }
}