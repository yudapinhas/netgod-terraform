module "data_bucket" {
  source      = "../../modules/data"
  bucket_name = var.bucket_name
  environment = var.environment
}

