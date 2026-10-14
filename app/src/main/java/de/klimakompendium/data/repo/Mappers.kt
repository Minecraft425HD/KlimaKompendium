package de.klimakompendium.data.repo

import de.klimakompendium.data.db.ControllerEntity
import de.klimakompendium.data.db.PlantPhotoEntity
import de.klimakompendium.data.db.PlantWithDetails
import de.klimakompendium.data.db.TermEntity
import de.klimakompendium.domain.model.AccessType
import de.klimakompendium.domain.model.Controller
import de.klimakompendium.domain.model.MeasurementKind
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.domain.model.PlantPhoto
import de.klimakompendium.domain.model.Term
import de.klimakompendium.domain.model.TermCategory
import de.klimakompendium.domain.model.TermLanguage

fun TermEntity.toDomain() = Term(
    id = id, key = key, abbreviation = abbreviation, language = TermLanguage.parse(language),
    fullTerm = fullTerm, termDe = termDe, termEn = termEn, termIt = termIt,
    kind = MeasurementKind.parse(kind), category = TermCategory.parse(category),
    unit = unit, meaning = meaning, context = context, typicalRange = typicalRange,
    isFavorite = isFavorite, isCustom = isCustom,
)

fun Term.toEntity() = TermEntity(
    id = id, key = key, abbreviation = abbreviation, language = language.name,
    fullTerm = fullTerm, termDe = termDe, termEn = termEn, termIt = termIt,
    kind = kind.name, category = category.name, unit = unit, meaning = meaning, context = context,
    typicalRange = typicalRange, isFavorite = isFavorite, isCustom = isCustom,
)

fun ControllerEntity.toDomain() = Controller(
    id = id, plantId = plantId, label = label, model = model, accessType = AccessType.parse(accessType),
    address = address, username = username, hasPassword = !passwordEnc.isNullOrEmpty(), notes = notes,
)

fun PlantPhotoEntity.toDomain() = PlantPhoto(id, plantId, filePath)

fun PlantWithDetails.toDomain() = Plant(
    id = plant.id, name = plant.name, customer = plant.customer, location = plant.location,
    buildYear = plant.buildYear, refrigerant = plant.refrigerant, manufacturer = plant.manufacturer,
    serialNumber = plant.serialNumber, controllerType = plant.controllerType, notes = plant.notes,
    lastOpenedAt = plant.lastOpenedAt,
    controllers = controllers.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.toDomain() },
    photos = photos.sortedBy { it.id }.map { it.toDomain() },
)
