module "data" {
  source        = "./modules/data"
  bucket_name   = var.bucket_name
  location      = var.location
  force_destroy = var.force_destroy
}
